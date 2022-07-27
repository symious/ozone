/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.ozone.util;

import org.apache.hadoop.fs.InvalidPathException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

/**
 * Util class that handles String as a Path.
 */
public class PathUtils {

  private final static String PATH_SEPARATOR = "/";

  public static String getPathParent(String pathString) {
    String normalizedString = normalizeAndCheck(pathString);
    if (normalizedString == null || normalizedString.equals(PATH_SEPARATOR)
        || !normalizedString.contains(PATH_SEPARATOR)) {
      return null;
    }
    return normalizedString.substring(0, normalizedString.lastIndexOf(PATH_SEPARATOR));

  }

  public static String getFileName(String pathString) {
    String[] splits = normalizeAndCheck(pathString).split(PATH_SEPARATOR);
    if (splits.length == 0) {
      return null;
    }
    return splits[splits.length - 1];

  }

  public static Iterator<String> iterator(String pathString) {
    return new Iterator<String>() {
      private int index = 0;
      private final String normalizedString = normalizeAndCheck(pathString);
      private final String[] splits = normalizedString.split(PATH_SEPARATOR);

      @Override
      public boolean hasNext() {
        return index < splits.length;
      }

      @Override
      public String next() {
        return splits[index++];
      }
    };
  }

  static String normalizeAndCheck(String input) {
    int n = input.length();
    char prevChar = 0;
    for (int i=0; i < n; i++) {
      char c = input.charAt(i);
      if ((c == '/') && (prevChar == '/'))
        return normalize(input, n, i - 1);
      checkNotNul(input, c);
      prevChar = c;
    }
    if (prevChar == '/')
      return normalize(input, n, n - 1);
    return input;
  }

  private static void checkNotNul(String input, char c) {
    if (c == '\u0000')
      throw new InvalidPathException(input, "Nul character not allowed");
  }

  private static String normalize(String input, int len, int off) {
    if (len == 0)
      return input;
    int n = len;
    while ((n > 0) && (input.charAt(n - 1) == '/')) n--;
    if (n == 0)
      return "/";
    StringBuilder sb = new StringBuilder(input.length());
    if (off > 0)
      sb.append(input.substring(0, off));
    char prevChar = 0;
    for (int i=off; i < n; i++) {
      char c = input.charAt(i);
      if ((c == '/') && (prevChar == '/'))
        continue;
      checkNotNul(input, c);
      sb.append(c);
      prevChar = c;
    }
    return sb.toString();
  }

  public static void main(String[] args) {
//    System.out.println(Paths.get("").getParent());
//    System.out.println(Paths.get("/").getParent());

    System.out.println(PathUtils.normalizeAndCheck("/d1/d2/d3/d4"));
    System.out.println(PathUtils.normalizeAndCheck("/d1/d2/d3/d4/"));
    System.out.println(PathUtils.getPathParent("/d1/d2/d3/d4"));
    System.out.println(PathUtils.getPathParent("/d1/d2/d3/d4/"));
    System.out.println(PathUtils.getPathParent("d1/d2/d3/d4/"));
    System.out.println(PathUtils.getPathParent("/"));
    System.out.println(PathUtils.getPathParent(""));
    System.out.println(PathUtils.getPathParent("d1"));

    System.out.println(PathUtils.getPathParent("/d1/d2/d3/d4"));

    Iterator<String> iterator = PathUtils.iterator("/d1/d2/d3/d4");
    System.out.println("Start print iterator:");
    while(iterator.hasNext()) {
      System.out.println(iterator.next());
    }

    Iterator<Path> iterator1 = Paths.get("/d1/d2/d3/d4").iterator();
    System.out.println("Start print iterator:");
    while(iterator1.hasNext()) {
      System.out.println(iterator1.next());
    }
     iterator1 = Paths.get("d1/d2/d3/d4").iterator();
    System.out.println("Start print iterator:");
    while(iterator1.hasNext()) {
      System.out.println(iterator1.next());
    }



  }
}
