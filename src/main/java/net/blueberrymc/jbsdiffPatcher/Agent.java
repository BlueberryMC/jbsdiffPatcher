package net.blueberrymc.jbsdiffPatcher;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.jar.JarFile;

public class Agent {
    public static Instrumentation inst;

    public static void premain(String arg, Instrumentation inst) {
        Agent.inst = inst;
    }

    public static int addToClassPath(Path file) {
        if (inst == null) {
            System.err.println("Could not retrieve Instrumentation API to add patched jar to classpath." +
                    "If you're running the patcher without -jar then you need to include the -javaagent:<patcher_jar>" +
                    "in JVM command line option.");
            return 1;
        }
        try {
            inst.appendToSystemClassLoaderSearch(new JarFile(file.toFile()));
        } catch (IOException e) {
            System.err.println("Error adding patched jar to classpath");
            e.printStackTrace();
            return 2;
        }
        return 0;
    }
}
