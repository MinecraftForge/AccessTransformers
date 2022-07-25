package net.minecraftforge.accesstransformer;

import joptsimple.*;
import joptsimple.util.*;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class TransformerProcessor {
    static {
        Configurator.initialize("", "atlog4j2.xml");
    }
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker AXFORM_MARKER = MarkerManager.getMarker("AXFORM");

    public static void main(String... args) {
        final OptionParser optionParser = new OptionParser();
        final ArgumentAcceptingOptionSpec<Path> inputJar = optionParser.accepts("inJar", "Input JAR file to apply transformation to").withRequiredArg().withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING)).required();
        final ArgumentAcceptingOptionSpec<Path> atFiles = optionParser.acceptsAll(list("atfile", "atFile"), "Access Transformer File").withRequiredArg().withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING)).required();
        final ArgumentAcceptingOptionSpec<Path> outputJar = optionParser.accepts("outJar", "Output JAR file").withRequiredArg().withValuesConvertedBy(new PathConverter());
        final ArgumentAcceptingOptionSpec<String> logFilePath = optionParser.accepts("logFile", "Log file for logging").withRequiredArg();

        final OptionSet optionSet;
        Path inputJarPath;
        Path outputJarPath;
        List<Path> atFilePaths;
        try {
            optionSet = optionParser.parse(args);
            final String logFile = logFilePath.value(optionSet);
            if (logFile != null) {
                // configure a custom logfile with debug level logging
                final LoggerContext logcontext = LoggerContext.getContext(false);
                final Configuration configuration = logcontext.getConfiguration();
                Appender fileAppender = FileAppender.newBuilder().
                        setName("logfile").
                        withFileName(logFile).
                        setLayout(configuration.getAppender("SysErr").getLayout()).
                        build();
                fileAppender.start();
                configuration.addAppender(fileAppender);
                configuration.getRootLogger().addAppender(fileAppender, Level.DEBUG, null);
                logcontext.updateLoggers();
                LOGGER.info(AXFORM_MARKER,"Writing debug log file {}", logFile);
            }
            inputJarPath = inputJar.value(optionSet).toAbsolutePath();
            final String s = inputJarPath.getFileName().toString();
            outputJarPath = outputJar.value(optionSet);
            if (outputJarPath == null) {
                outputJarPath = inputJarPath.resolveSibling(s.substring(0,s.length()-4)+"-new.jar");
            } else {
                outputJarPath = outputJarPath.toAbsolutePath();
            }

            atFilePaths = atFiles.values(optionSet).stream().map(Path::toAbsolutePath).collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.error(AXFORM_MARKER,"Option Parsing Error", e);
            return;
        }
        LOGGER.info(AXFORM_MARKER, "Access Transformer processor running version {}", TransformerProcessor.class.getPackage().getImplementationVersion());
        LOGGER.info(AXFORM_MARKER, "Command line arguments {}", Arrays.asList(args));
        LOGGER.info(AXFORM_MARKER,"Reading from {}", inputJarPath);
        LOGGER.info(AXFORM_MARKER,"Writing to {}", outputJarPath);
        atFilePaths.forEach(path -> LOGGER.info(AXFORM_MARKER,"Transformer file {}", path));
        try {
            LOGGER.warn("Found existing output jar {}, overwriting", outputJarPath);
            Files.deleteIfExists(outputJarPath);
        } catch (IOException e) {
            LOGGER.error(AXFORM_MARKER,"Deleting existing out JAR", e);
        }
        processJar(inputJarPath, outputJarPath, atFilePaths);
        LOGGER.info(AXFORM_MARKER,"JAR transformation complete {}", outputJarPath);
    }

    private static List<String> list(String... vars) {
        return Arrays.asList(vars);
    }

    @SuppressWarnings("serial")
    private static void processJar(final Path inputJar, final Path outputJarPath, final List<Path> atFilePaths) {
        atFilePaths.forEach(path -> {
            AccessTransformerEngine.INSTANCE.addResource(path, path.getFileName().toString());
            LOGGER.debug(AXFORM_MARKER,"Loaded access transformer file {}", path);
        });

        final URI toUri = outputJarPath.toUri();
        final URI outJarURI = URI.create("jar:"+toUri.toASCIIString());
        try (FileSystem outJar = FileSystems.newFileSystem(outJarURI, new HashMap<String, String>() {{
            put("create", "true");
        }})) {
            //final Path outRoot = StreamSupport.stream(outJar.getRootDirectories().spliterator(), false).findFirst().get();
            try (FileSystem jarFile = FileSystems.newFileSystem(inputJar, ClassLoader.getSystemClassLoader())) {
                Files.walk(StreamSupport.stream(jarFile.getRootDirectories().spliterator(), false).findFirst().orElseThrow(() -> new IllegalArgumentException("The JAR has no root?!")))
                        .forEach(path -> {
                            Path outPath = outJar.getPath(path.toAbsolutePath().toString());
                            if (path.getNameCount() > 0 && String.valueOf(path.getFileName()).endsWith(".class")) {
                                try (InputStream is = Files.newInputStream(path)) {
                                    final ClassReader classReader = new ClassReader(is);
                                    final ClassNode cn = new ClassNode();
                                    classReader.accept(cn, 0);
                                    final Type type = Type.getType('L'+cn.name.replace('.', '/')+';');
                                    if (AccessTransformerEngine.INSTANCE.handlesClass(type)) {
                                        LOGGER.debug(AXFORM_MARKER,"Transforming class {}", type);
                                        AccessTransformerEngine.INSTANCE.transform(cn, type);
                                        ClassWriter cw = new ClassWriter(Opcodes.ASM5);
                                        cn.accept(cw);
                                        Files.write(outPath, cw.toByteArray());
                                    } else {
                                        LOGGER.debug(AXFORM_MARKER,"Skipping {}", type);
                                        Files.copy(path, outPath);
                                    }
                                } catch (IOException e) {
                                    LOGGER.error(AXFORM_MARKER,"Reading {}", path, e);
                                }
                            } else if (!Files.exists(outPath)){
                                try {
                                    Files.copy(path, outPath);
                                } catch (IOException e) {
                                    LOGGER.error(AXFORM_MARKER,"Copying {}", path, e);
                                }
                            }
                        });
            } catch (IOException e) {
                LOGGER.error(AXFORM_MARKER,"Reading JAR", e);
            }
        } catch (IOException e) {
            LOGGER.error(AXFORM_MARKER,"Writing JAR", e);
        }
    }
}
