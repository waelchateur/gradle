package org.gradle.internal.classloader;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;
import org.jetbrains.annotations.Nullable;

public class DexBackedURLClassLoader extends DexClassLoader {

  private static final Set<String> PARENT_FIRST = new HashSet<>();

  static {
    PARENT_FIRST.add(DexBackedURLClassLoader.class.getName());
    PARENT_FIRST.add("java");
    PARENT_FIRST.add("javax");
  }

  private final URLClassLoader urlClassLoader;
  private final HashCode implementationHash;

  public DexBackedURLClassLoader(ClassLoader parent) {
    this("", parent, ClassPath.EMPTY, null);
  }

  public DexBackedURLClassLoader(
      String name, ClassLoader parent, ClassPath classPath, HashCode implementationHash) {
    super("", null, null, parent);
    urlClassLoader = new URLClassLoader(classPath.getAsURLArray(), parent);
    this.implementationHash = implementationHash;
  }

  @Override
  protected Class<?> findClass(String moduleName, String name) {
    return super.findClass(moduleName, name);
  }

  @Override
  public Class<?> findClass(String name) throws ClassNotFoundException {
    try {
      if (PARENT_FIRST.contains(name) || name.startsWith("java.") || name.startsWith("javax.")) {
        return Class.forName(name);
      }
    } catch (ClassNotFoundException ignored) {
    }

    try {
      return super.findClass(name);
    } catch (ClassNotFoundException e) {
      String resourcePath = name.replace('.', '/') + ".class";
      URL resource = urlClassLoader.getResource(resourcePath);

      if (resource == null) {
        throw e;
      }

      String stringResource = resource.toString();
      String jarPath =
          stringResource.substring(
              stringResource.indexOf(":") + 1, stringResource.lastIndexOf('!'));
      return super.findClass(name);
    }
  }

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    return super.loadClass(name);
  }

  @Override
  public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    return super.loadClass(name, resolve);
  }

  @Override
  protected URL findResource(String name) {
    URL resource = super.findResource(name);
    if (resource != null) {
      return resource;
    }
    return urlClassLoader.findResource(name);
  }

  @Nullable
  @Override
  public URL getResource(String name) {
    return super.getResource(name);
  }

  protected void compileJar(String path) {
    File jarFile = new File(URI.create(path).getPath());
    File dexFile = new File(jarFile.getParentFile(), jarFile.getName().replace(".jar", ".zip"));

    dexJar(jarFile, dexFile);

    addDexPathPublic(dexFile);
  }

  public static void dexJar(File inputJar, File outputDir) {
    D8Command.Builder builder = D8Command.builder();
    builder.setMode(CompilationMode.RELEASE);
    builder.setMinApiLevel(26);
    builder.addProgramFiles(inputJar.toPath());

    builder.setOutput(outputDir.toPath(), OutputMode.DexIndexed);
    try {
      D8.run(builder.build());
    } catch (CompilationFailedException e) {
      throw new GradleException(e.getMessage(), e.getCause());
    }
  }

  public void addDexPathPublic(String path) {
    addDexPathPublic(new File(path));
  }

  public void addDexPathPublic(File path) {
    try {
      path.setWritable(false);
      addDexToClasspath(path, this);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("DiscouragedPrivateApi")
  private void addDexToClasspath(File dex, ClassLoader classLoader) throws Exception {
    Class<?> dexClassLoaderClass = Class.forName(BaseDexClassLoader.class.getName());
    Field pathListField = dexClassLoaderClass.getDeclaredField("pathList");
    pathListField.setAccessible(true);
    Object pathList = pathListField.get(classLoader);
    Method addDexPath =
        pathList.getClass().getDeclaredMethod("addDexPath", String.class, File.class);
    addDexPath.setAccessible(true);
    addDexPath.invoke(pathList, dex.getAbsolutePath(), null);
  }
}
