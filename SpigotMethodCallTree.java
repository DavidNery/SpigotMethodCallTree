package me.dery.testes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class SpigotMethodCallTree {

    public static void main(String[] args) throws IOException {
        /*
         class
         |- method
            |- classes using
               |- method of class that is using
        */
        HashMap<String, HashMap<String, HashMap<String, List<String>>>> tree = new HashMap<>();

        JarFile jarFile = new JarFile("path/to/spigot/jar");
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            if (!entry.getName().endsWith(".class")) continue;
            if (entry.getName().startsWith("org/bukkit/craftbukkit/libs")) continue;
            if (!entry.getName().startsWith("org/bukkit")
                    && !entry.getName().startsWith("net/minecraft")
                    && !entry.getName().startsWith("com/mojang")) continue;

            ClassReader clazz = new ClassReader(jarFile.getInputStream(entry));
            ClassNode node = new ClassNode();
            clazz.accept(node, ClassReader.EXPAND_FRAMES);

            parseClassMethods(tree, node);
        }

        jarFile.close();

        File dir = new File("path/to/dest/files");
        dir.mkdirs();

        for (Map.Entry<String, HashMap<String, HashMap<String, List<String>>>> usedClasses : tree.entrySet()) {
            File file = new File(dir, usedClasses.getKey().replace("/", "."));
            file.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(file);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);

            JsonObject json = new JsonObject();

            for (Map.Entry<String, HashMap<String, List<String>>> usedMethods : usedClasses.getValue().entrySet()) {
                JsonObject classesUsingJson = new JsonObject();
                for (Map.Entry<String, List<String>> classesUsing : usedMethods.getValue().entrySet()) {
                    JsonArray methodUsingJson = new JsonArray();
                    for (String methodUsing : classesUsing.getValue()) {
                        methodUsingJson.add(methodUsing);
                    }

                    classesUsingJson.add(classesUsing.getKey().replace("/", "."), methodUsingJson);
                }

                json.add(usedMethods.getKey(), classesUsingJson);
            }

            bufferedOutputStream.write(json.toString().getBytes(StandardCharsets.UTF_8));
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
        }
    }

    private static void parseClassMethods(
            HashMap<String, HashMap<String, HashMap<String, List<String>>>> tree,
            ClassNode node
    ) {
        for (MethodNode method : node.methods) {
            InsnList instructions = method.instructions;

            for (int i = 0; i < instructions.size(); i++) {
                AbstractInsnNode ins = instructions.get(i);
                switch (ins.getOpcode()) {
                    case Opcodes.INVOKEVIRTUAL:
                    case Opcodes.INVOKESPECIAL:
                    case Opcodes.INVOKESTATIC:
                    case Opcodes.INVOKEINTERFACE:
                        MethodInsnNode methodInsnNode = (MethodInsnNode) ins;

                        String owner = methodInsnNode.owner;
                        if (!owner.startsWith("org/bukkit")
                                && !owner.startsWith("net/minecraft")
                                && !owner.startsWith("com/mojang")) continue;

                        tree.compute(methodInsnNode.owner, (key, map) -> {
                            if (map == null) map = new HashMap<>();

                            String methodName = methodInsnNode.name;
                            Type[] arguments = Type.getArgumentTypes(methodInsnNode.desc);
                            if (arguments.length == 0)
                                methodName += "()";
                            else
                                methodName += "(" + Arrays.stream(arguments)
                                        .map(Type::getClassName).collect(Collectors.joining(", ")) + ")";

                            map.compute(methodName, (methodKey, methodMap) -> {
                                if (methodMap == null) methodMap = new HashMap<>();
                                methodMap.compute(node.name, (invokingName, invokingList) -> {
                                    if (invokingList == null) invokingList = new ArrayList<>();

                                    String invokingMethodName = method.name;
                                    Type[] invokingArguments = Type.getArgumentTypes(method.desc);
                                    if (arguments.length == 0)
                                        invokingMethodName += "()";
                                    else
                                        invokingMethodName += "(" + Arrays.stream(invokingArguments)
                                                .map(Type::getClassName).collect(Collectors.joining(", ")) + ")";
                                    invokingList.add(invokingMethodName);

                                    return invokingList;
                                });

                                return methodMap;
                            });

                            return map;
                        });

                }
            }
        }
    }

}
