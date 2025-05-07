/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.nativeintegration.jna;

import static org.gradle.reflection.android.AndroidSupport.isDalvik;
import static org.gradle.reflection.android.AndroidSupport.isRunningAndroid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.management.ManagementFactory;
import java.util.Map;
import org.gradle.internal.nativeintegration.EnvironmentModificationResult;
import org.gradle.internal.nativeintegration.NativeIntegrationException;
import org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnsupportedEnvironment implements ProcessEnvironment {
  private static final Logger LOGGER = LoggerFactory.getLogger(UnsupportedEnvironment.class);

  private final Long pid;

  public UnsupportedEnvironment() {
    pid = extractPIDFromRuntimeMXBeanName();
  }

  /**
   * The default format of the name of the Runtime MX bean is PID@HOSTNAME. The PID is parsed
   * assuming that is the format.
   *
   * <p>This works on Solaris and should work with any Java VM
   */
  private Long extractPIDFromRuntimeMXBeanName() {
    // deenu modify: return pid
    if (isRunningAndroid() || isDalvik()) {
      try {
        Integer pidInt =
            (Integer) Class.forName("android.os.Process").getDeclaredMethod("myPid").invoke(null);

        Long pid_ = Long.parseLong(String.valueOf(pidInt));
        if (pid_ != null) {
          return pid_;
        }
      } catch (ReflectiveOperationException | UnsatisfiedLinkError ignored) {
      }

      try {
        File self = new File("/proc/self");
        Long pid_ = Long.parseLong(self.getCanonicalFile().getName());
        if (pid_ != null) {
          return pid_;
        }
      } catch (Exception ignored) {
      }

      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new FileReader("/proc/self/stat"));
        String line = reader.readLine();
        if (line != null) {
          String[] parts = line.split(" ");

          Long pid_ = Long.parseLong(parts[0]);
          if (pid_ != null) {
            return pid_;
          }
        }
      } catch (Exception ignored) {
      } finally {
        if (reader != null) {
          try {
            reader.close();
          } catch (Exception ignored) {
          }
        }
      }
    } else {
      String runtimeMXBeanName = ManagementFactory.getRuntimeMXBean().getName();
      int separatorPos = runtimeMXBeanName.indexOf('@');
      if (separatorPos > -1) {
        try {
          Long pid_ = Long.parseLong(runtimeMXBeanName.substring(0, separatorPos));
          if (pid_ != null) {
            return pid_;
          }
        } catch (NumberFormatException e) {
          LOGGER.debug(
              "Native-platform process: failed to parse PID from Runtime MX bean name: "
                  + runtimeMXBeanName);
        }
      } else {
        LOGGER.debug("Native-platform process: failed to parse PID from Runtime MX bean name");
      }
    }
    return null;
  }

  @Override
  public EnvironmentModificationResult maybeSetEnvironment(Map<String, String> source) {
    return EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT;
  }

  @Override
  public void removeEnvironmentVariable(String name) throws NativeIntegrationException {
    throw notSupported();
  }

  @Override
  public EnvironmentModificationResult maybeRemoveEnvironmentVariable(String name) {
    return EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT;
  }

  @Override
  public void setEnvironmentVariable(String name, String value) throws NativeIntegrationException {
    throw notSupported();
  }

  @Override
  public EnvironmentModificationResult maybeSetEnvironmentVariable(String name, String value) {
    return EnvironmentModificationResult.UNSUPPORTED_ENVIRONMENT;
  }

  @Override
  public File getProcessDir() throws NativeIntegrationException {
    throw notSupported();
  }

  @Override
  public void setProcessDir(File processDir) throws NativeIntegrationException {
    throw notSupported();
  }

  @Override
  public boolean maybeSetProcessDir(File processDir) {
    return false;
  }

  @Override
  public Long getPid() throws NativeIntegrationException {
    if (pid != null) {
      return pid;
    }
    throw notSupported();
  }

  @Override
  public Long maybeGetPid() {
    return pid;
  }

  @Override
  public boolean maybeDetachProcess() {
    return false;
  }

  @Override
  public void detachProcess() {
    throw notSupported();
  }

  private NativeIntegrationException notSupported() {
    return new NativeIntegrationUnavailableException(
        "We don't support this operating system: " + OperatingSystem.current());
  }
}
