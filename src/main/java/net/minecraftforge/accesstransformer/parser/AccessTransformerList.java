/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.accesstransformer.parser;

import net.minecraftforge.accesstransformer.ClassTarget;
import net.minecraftforge.accesstransformer.WildcardTarget;
import net.minecraftforge.accesstransformer.FieldTarget;
import net.minecraftforge.accesstransformer.MethodTarget;
import net.minecraftforge.accesstransformer.InnerClassTarget;
import net.minecraftforge.accesstransformer.INameHandler;
import net.minecraftforge.accesstransformer.IdentityNameHandler;
import net.minecraftforge.accesstransformer.AccessTransformer;
import net.minecraftforge.accesstransformer.AccessTransformer.FinalState;
import net.minecraftforge.accesstransformer.AccessTransformer.Modifier;
import net.minecraftforge.accesstransformer.Target;
import net.minecraftforge.accesstransformer.TargetType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AccessTransformerList {
    private static final Logger LOGGER = LogManager.getLogger("AXFORM");
    private static final Marker AXFORM_MARKER = MarkerManager.getMarker("AXFORM");
    private final Map<Target<?>, AccessTransformer> accessTransformers = new HashMap<>();
    private final Set<Type> validAtTypes = new HashSet<>();
    private final Renamer renamer = new Renamer();
    private INameHandler nameHandler = new IdentityNameHandler();

    public void loadFromResource(String resourceName) throws URISyntaxException, IOException {
        final Path path = Paths.get(getClass().getClassLoader().getResource(resourceName).toURI());
        loadFromPath(path, resourceName);
    }

    public void loadFromPath(Path path, String resourceName) throws IOException {
        LOGGER.debug(AXFORM_MARKER, "Loading access transformer {} from path {}", resourceName, path);
        load(path, resourceName, Files.readAllLines(path));
    }

    public void load(Path path, String resourceName, List<String> lines) {
        boolean failed = false;
        List<AccessTransformer> ats = new ArrayList<>();
        int lineIndex = -1;
        for (String line : lines) {
            lineIndex++;
            List<String> tokens = tokenize(line.trim());
            if (tokens.isEmpty())
                continue;

            if (tokens.size() < 2 || tokens.size() > 3) {
                LOGGER.error(AXFORM_MARKER, "Invalid access transformer line in {}: {}", resourceName, line);
                failed = true;
                continue;
            }

            Modifier mod = ModifierProcessor.modifier(tokens.get(0));
            if (mod == null) {
                LOGGER.error(AXFORM_MARKER, "Invalid access transformer line in {}: {}", resourceName, line);
                failed = true;
                continue;
            }

            FinalState fmod = ModifierProcessor.finalState(tokens.get(0));
            Target<?> target = null;

            String cls = tokens.get(1).replace('.', '/');
            String name = tokens.size() == 2 ? null : tokens.get(2);

            if (tokens.size() == 2) { // Class
                target = new ClassTarget(renamer.map(cls));
                //Java uses this to identify inner classes, Scala/others use it for synthetics. Either way we should be fine as it will skip over classes that don't exist.
                int idx = target.getClassName().lastIndexOf('$');
                if (idx != -1) {
                    String parent = target.getClassName().substring(0, idx);
                    ats.add(new AccessTransformer(new InnerClassTarget(parent, target.getClassName()), mod, fmod, resourceName, lineIndex));
                }
            } else if ("*".equals(name)) { // Field Wildcard
                target = new WildcardTarget(renamer.map(cls), false);
            } else if ("*()".equals(name)) { // Method Wildcard
                target = new WildcardTarget(renamer.map(cls), true);
            } else if (name.indexOf('(') == -1) { // Fields
                target = new FieldTarget(renamer.map(cls), renamer.mapFieldName(cls, name, null));
            } else { // Methods
                int idx = name.indexOf('(');
                String desc = name.substring(idx).replace('.', '/');
                name = name.substring(0, idx);
                target = new MethodTarget(renamer.map(cls), renamer.mapMethodName(cls, name, desc), renamer.mapMethodDesc(desc));
            }
            ats.add(new AccessTransformer(target, mod, fmod, resourceName, lineIndex));
        }

        if (failed)
            throw new IllegalArgumentException("Invalid AccessTransformer config, see log for details");

        final HashMap<Target<?>, AccessTransformer> localATCopy = new HashMap<>(accessTransformers);
        mergeAccessTransformers(ats, localATCopy, resourceName);
        final List<AccessTransformer> invalidTransformers = invalidTransformers(localATCopy);
        if (!invalidTransformers.isEmpty()) {
            invalidTransformers.forEach(at -> LOGGER.error(AXFORM_MARKER,"Invalid access transform final state for target {}. Referred in resources {}.", at.getTarget(), at.getOrigins()));
            throw new IllegalArgumentException("Invalid AT final conflicts");
        }
        this.accessTransformers.clear();
        this.accessTransformers.putAll(localATCopy);
        for (AccessTransformer newAT : ats)
            this.validAtTypes.add(newAT.getTarget().getASMType());
        LOGGER.debug(AXFORM_MARKER,"Loaded access transformer {} from path {}", resourceName, path);
    }

    private static List<String> tokenize(String line) {
        if (line.length() == 0)
            return Collections.emptyList();

        // This is unrolled instead of using String.split because that uses RegEx in the back end which is slow
        List<String> ret = new ArrayList<>();
        int start = 0;
        for (int x = 0; x < line.length(); x++) {
            char c = line.charAt(x);
            if (c == ' ' || c == '\t') {
                if (start == x)
                    ret.add("");
                else
                    ret.add(line.substring(start, x));

                // Skip all consecutive whitespace
                do {
                    x++;
                    if (x == line.length())
                        break;
                    c = line.charAt(x);
                } while (c == ' ' || c == '\t');

                if (c == '#')
                    break;

                start = x--;
            } else if (x == line.length() - 1) {
                ret.add(line.substring(start));
            } else if (c == '#') {
                if (start != x)
                    ret.add(line.substring(start, x));
                break;
            }
        }
        return ret;
    }

    private void mergeAccessTransformers(List<AccessTransformer> atList, Map<Target<?>, AccessTransformer> accessTransformers, String resourceName) {
        for (AccessTransformer at : atList) {
            accessTransformers.merge(at.getTarget(), at, (at1, at2) -> at1.mergeStates(at2, resourceName));
        }
    }

    private List<AccessTransformer> invalidTransformers(HashMap<Target<?>, AccessTransformer> accessTransformers) {
        List<AccessTransformer> ret = new ArrayList<>();
        for (AccessTransformer at : accessTransformers.values()) {
            if (!at.isValid())
                ret.add(at);
        }
        return ret;
    }

    public Map<String, List<AccessTransformer>> getAccessTransformers() {
        Map<String, List<AccessTransformer>> ret = new HashMap<>();
        accessTransformers.forEach((k, v) -> {
            ret.computeIfAbsent(v.getTarget().getClassName(), t -> new ArrayList<>()).add(v);
        });
        return ret;
    }

    public boolean containsClassTarget(Type type) {
        return this.validAtTypes.contains(type);
    }

    public Map<TargetType, Map<String, AccessTransformer>> getTransformersForTarget(Type type) {
        Map<TargetType, Map<String, AccessTransformer>> ret = new HashMap<>();
        accessTransformers.forEach((k, v) -> {
            if (!type.equals(k.getASMType()))
                return;
            ret.computeIfAbsent(v.getTarget().getType(), t -> new HashMap<>())
                .put(v.getTarget().targetName(), v);
        });
        return ret;
    }

    public void setNameHandler(final INameHandler nameHandler) {
        this.nameHandler = nameHandler;
        LOGGER.debug(AXFORM_MARKER, "Set name handler {}", nameHandler);
    }

    private final class Renamer extends Remapper {
        @Override
        public String map(String internalName) {
            return AccessTransformerList.this.nameHandler.translateClassName(internalName);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            return AccessTransformerList.this.nameHandler.translateFieldName(name);
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            return AccessTransformerList.this.nameHandler.translateMethodName(name);
        }
    }
}
