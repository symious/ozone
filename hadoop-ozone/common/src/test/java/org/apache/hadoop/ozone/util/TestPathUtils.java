/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.hadoop.ozone.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

/**
 * Test the result of PathUtils and Paths
 */
public class TestPathUtils {

  public static String[] testCases() {
    return new String[] {
        "",
        "/",
        "/d1",
        "/d1/",
        "/d1/d2",
        "/d1/d2/",
        "d1",
        "d1/",
        "d1/d2",
        "d1/d2/",
    };
  };

  @ParameterizedTest
  @MethodSource("testCases")
  public void testIterate(String str) {
    System.out.println("Test str: " + str);
    Iterator<Path> pathIterator = Paths.get(str).iterator();
    Iterator<String> utilIterator = PathUtils.iterator(str);

    while (pathIterator.hasNext()) {
      Assertions.assertEquals(pathIterator.next().toString(),
          utilIterator.next());
    }
    Assertions.assertFalse(utilIterator.hasNext());
  }

  @ParameterizedTest
  @MethodSource("testCases")
  public void testParentPath(String str) {
    Assertions.assertEquals(PathUtils.getPathParent(str), Paths.get(str).getParent().toString());
  }

  @ParameterizedTest
  @MethodSource("testCases")
  public void testFileName(String str) {
    Assertions.assertEquals(PathUtils.getFileName(str), Paths.get(str).getFileName().toString());
  }
}
