/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package dev.failsafe.retrofit;

import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Manifest;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class ModuleManifestTest {
  public void shouldExposeCorrectAutomaticModuleName() throws Exception {
    Path manifestPath = moduleManifestPath();
    assertTrue(Files.exists(manifestPath), "Module manifest should exist at " + manifestPath);
    try (InputStream in = Files.newInputStream(manifestPath)) {
      Manifest manifest = new Manifest(in);
      assertEquals(manifest.getMainAttributes().getValue("Automatic-Module-Name"), "dev.failsafe.retrofit");
    }
  }

  private static Path moduleManifestPath() throws Exception {
    Path outputDirectory = Paths.get(ModuleManifestTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    if ("test-classes".equals(outputDirectory.getFileName().toString())) {
      outputDirectory = outputDirectory.getParent().resolve("classes");
    }
    return outputDirectory.resolve("META-INF/MANIFEST.MF");
  }
}
