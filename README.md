# Spigot Method Call Tree
In this repository you can check where any method of spigot classes are invoked.

# How I extracted this?
To do this, I used Java 8 + [ASM](https://asm.ow2.io/) following the steps below:

1. I load the jar with the native Java API as `JarFile`;
2. I loop through all the class files in the loaded jar;
3. I check if the class belongs to the `com.mojang`,` org.bukkit` or `net.minecraft` packages;
4. I loop through all method invocation statements of all methods of the filtered classes;
5. If the method owner is one of the three packages used before, I add it to a hashmap;
6. After that, I loop the hashmap, creating a folder for each class that invoked methods and inside that folder I create a json file with the following invocation tree:
```
|- Class method that is used by other classes
|--- Class that is using the method
|----- Method of the class that is using the method
```

You can check all the code used in the file [SpigotMethodCallTree](./SpigotMethodCallTree.java). Yes, I know the code is horrible, but I wasn't focused on performance or anything like that. I was just focused on do it!

# Extra
I used Paper 1.16.1 to extract the invocation tree