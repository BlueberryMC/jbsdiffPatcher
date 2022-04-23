package net.blueberrymc.jbsdiffPatcher;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import net.blueberrymc.nativeutil.NativeUtil;
import org.apache.commons.compress.compressors.CompressorException;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarInputStream;

// You have to put the these required files manually:
// /patch.properties
// /patch.bz2
public class Patcher {
    public static boolean headless = GraphicsEnvironment.isHeadless() || Boolean.getBoolean("blueberry.nogui");
    public static JFrame frame = null;
    public static final JLabel status = new JLabel();
    public static final JProgressBar progress = new JProgressBar();
    public static final JButton close = new JButton();

    private static void setVisible() {
        if (frame != null) {
            if (frame.isVisible()) return;
            if (!(GraphicsEnvironment.isHeadless() || Boolean.getBoolean("blueberry.nogui"))) {
                frame.setVisible(true);
            }
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("nogui")) {
            headless = true;
        }
        if (!headless) {
            frame = new JFrame();
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.out.println("L&F Initialization failed, using default theme.");
                e.printStackTrace();
            }
        }
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.CENTER, 100000000, 5));
        status.setText("Setting up environment");
        close.setText("Close");
        close.setEnabled(false);
        close.addActionListener(e -> System.exit(0));
        panel.add(status);
        panel.add(progress);
        panel.add(close);
        if (frame != null) {
            frame.add(panel);
            frame.setSize(400, 120);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            if (Boolean.getBoolean("blueberry.openFrameOnStartup")) setVisible();
            frame.setTitle("Minecraft Patcher");
        }
        Path path = setup();
        if (path != null) {
            status.setText("Starting Minecraft...");
            progress.setValue(70);
            String mainClass;
            try {
                mainClass = getMainClass(path);
                if (mainClass == null) {
                    setVisible();
                    status.setText("Could not find main class");
                    progress.setValue(100);
                    close.setEnabled(true);
                    System.err.println("Could not find main class");
                    return;
                }
            } catch (IOException ex) {
                setVisible();
                status.setText("Error: Could not read patched jar");
                progress.setValue(100);
                close.setEnabled(true);
                System.err.println("Error reading patched jar");
                ex.printStackTrace();
                return;
            }
            NativeUtil.appendToSystemClassLoaderSearch(path.toFile().getAbsolutePath());
            progress.setValue(80);
            Method method;
            try {
                Class<?> clazz = Class.forName(mainClass, true, ClassLoader.getSystemClassLoader());
                method = clazz.getMethod("main", String[].class);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                setVisible();
                status.setText("Error: Could not load main class or no main method");
                progress.setValue(100);
                close.setEnabled(true);
                System.err.println("Error loading main class");
                e.printStackTrace();
                return;
            }
            System.out.println("Invoking wrapped instance: " + method.getDeclaringClass().getCanonicalName() + " / " + method.toGenericString());
            progress.setValue(100);
            if (frame != null) {
                frame.setVisible(false);
            }
            try {
                method.invoke(null, (Object) args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                setVisible();
                status.setText("Error: Could not invoke main method");
                close.setEnabled(true);
                System.err.println("Error invoking main method");
                e.printStackTrace();
            }
        }
    }

    public static Path setup() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            setVisible();
            status.setText("Error: Could not create hashing instance");
            progress.setValue(100);
            close.setEnabled(true);
            System.err.println("Could not create hashing instance");
            e.printStackTrace();
            return null;
        }
        status.setText("Reading patch data");
        PatchData data;
        try {
            InputStream in = Patcher.class.getResourceAsStream("/patch.properties");
            if (in == null) {
                status.setText("Error: No patch file found.");
                progress.setValue(100);
                close.setEnabled(true);
                System.err.println("Error: No patch file found.");
                return null;
            }
            Properties properties = new Properties();
            properties.load(new BufferedReader(new InputStreamReader(in)));
            data = new PatchData(
                    properties.getProperty("name"),
                    properties.getProperty("version"),
                    properties.getProperty("vanillaUrl"),
                    properties.getProperty("vanillaHash"),
                    properties.getProperty("patchedHash")
            );
        } catch (NullPointerException | IOException ex) {
            status.setText("Error: Could not read patch file");
            progress.setValue(100);
            close.setEnabled(true);
            System.err.println("Error reading patch file");
            ex.printStackTrace();
            return null;
        }
        return validateJar(digest, data);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static Path validateJar(MessageDigest digest, PatchData data) {
        progress.setValue(20);
        status.setText("Validating vanilla jar...");
        Path cache = Paths.get("cache");
        cache.toFile().mkdirs();
        Path vanillaJar = validateVanillaJar(digest, cache, data);
        if (vanillaJar == null) return null;
        progress.setValue(40);
        status.setText("Validating patched jar...");
        Path patchedJar = validatePatchedJar(digest, cache, data, vanillaJar);
        if (patchedJar == null) return null;
        progress.setValue(60);
        if (Boolean.getBoolean("blueberry.patchOnly")) {
            System.exit(0);
            return null;
        }
        return patchedJar;
    }

    public static Path validateVanillaJar(MessageDigest digest, Path cache, PatchData data) {
        Path path = cache.resolve("vanilla_" + data.version + ".jar");
        if (isDirty(digest, path, data.vanillaHash)) {
            setVisible();
            status.setText("Downloading vanilla jar...");
            System.out.println("Downloading vanilla jar...");
            FileDownload.download(path, data.vanillaUrl);
            progress.setValue(30);
            if (isDirty(digest, path, data.vanillaHash)) {
                status.setText("Error: Downloaded vanilla jar file doesn't match the expected hash");
                progress.setValue(100);
                close.setEnabled(true);
                System.err.println("Error: Downloaded vanilla jar file doesn't match the expected hash");
                System.err.println("Expected: " + bytesToHex(data.vanillaHash));
                System.err.println("Actual: " + bytesToHex(digest.digest(readBytes(path))));
                return null;
            }
        }
        return path;
    }

    public static Path validatePatchedJar(MessageDigest digest, Path cache, PatchData data, Path vanillaJar) {
        Path path = cache.resolve("patched_" + data.version + ".jar");
        if (isDirty(digest, path, data.patchedHash)) {
            setVisible();
            try {
                status.setText("Patching the vanilla jar...");
                System.out.println("Patching the vanilla jar...");
                Patch.patch(readBytes(vanillaJar), readFully(Objects.requireNonNull(Patcher.class.getResourceAsStream("/patch.bz2"))), Files.newOutputStream(path));
            } catch (CompressorException | IOException | InvalidHeaderException e) {
                status.setText("Error: Failed to patch the vanilla jar");
                progress.setValue(100);
                close.setEnabled(true);
                System.err.println("Failed to patch the vanilla jar");
                e.printStackTrace();
                return null;
            }
            progress.setValue(50);
            if (isDirty(digest, path, data.patchedHash)) {
                status.setText("Error: Patched jar file doesn't match the expected hash");
                progress.setValue(100);
                close.setEnabled(true);
                System.err.println("Error: Patched jar file doesn't match the expected hash");
                System.err.println("Expected: " + bytesToHex(data.patchedHash));
                System.err.println("Actual: " + bytesToHex(digest.digest(readBytes(path))));
                return null;
            }
        }
        return path;
    }

    public static byte[] readFully(final InputStream in) throws IOException {
        try {
            byte[] buffer = new byte[16 * 1024];
            int off = 0;
            int read;
            while ((read = in.read(buffer, off, buffer.length - off)) != -1) {
                off += read;
                if (off == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
            }
            return Arrays.copyOfRange(buffer, 0, off);
        } finally {
            in.close();
        }
    }

    public static byte[] readBytes(final Path file) {
        try {
            return readFully(Files.newInputStream(file));
        } catch (final IOException e) {
            System.err.println("Failed to read some data from " + file.toAbsolutePath());
            e.printStackTrace();
            System.exit(1);
            throw new AssertionError();
        }
    }

    public static boolean isDirty(MessageDigest digest, Path file, byte[] hash) {
        if (!Files.exists(file)) return true;
        byte[] bytes = readBytes(file);
        return !Arrays.equals(hash, digest.digest(bytes));
    }

    public static String getMainClass(Path file) throws IOException {
        try (
                InputStream in = new BufferedInputStream(Files.newInputStream(file));
                JarInputStream js = new JarInputStream(in)
        ) {
            return js.getManifest().getMainAttributes().getValue("Main-Class");
        }
    }

    public static String bytesToHex(byte[] bytes) {
        final StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            final String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
