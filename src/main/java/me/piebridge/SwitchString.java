package me.piebridge;

import org.openjdk.jmh.annotations.*;

import static java.lang.Integer.*;
import static java.lang.System.*;

public class SwitchString {

  static final String[] KEYS = {
    "AaAa", "AaBB",
    "BBAa", "BBBB",
  };

  static int switchString(String k) {
    switch (k) {
      case "AaAa":
      case "AaBB":
      case "BBAa":
      case "BBBB":
        return 1;
      default:
        return -1;
    }
  }

  static int switchHash(String k) {
    byte var2 = -1;
    switch (k.hashCode()) {
      case 2031744:
        if (k.equals("BBBB")) {
          var2 = 3;
        } else if (k.equals("BBAa")) {
          var2 = 2;
        } else if (k.equals("AaBB")) {
          var2 = 1;
        } else if (k.equals("AaAa")) {
          var2 = 0;
        }
      default:
        switch (var2) {
          case 0:
          case 1:
          case 2:
          case 3:
            return 1;
          default:
            return -1;
        }
    }
  }

  static int ifElse(String key) {
    if ("AaAa".equals(key)
      || "AaBB".equals(key)
      || "BBAa".equals(key)
      || "BBBB".equals(key)) {
      return 1;
    } else {
      return -1;
    }
  }

  public static void main(String[] a) {
    int round = parseInt(a[0]);
    long s1 = currentTimeMillis();
    for (int i = 0; i < round; ++i) {
      for (String key : KEYS) {
        switchString(key);
      }
    }
    long s2 = currentTimeMillis();
    for (int i = 0; i < round; ++i) {
      for (String key : KEYS) {
        switchHash(key);
      }
    }
    long s3 = currentTimeMillis();
    for (int i = 0; i < round; ++i) {
      for (String key : KEYS) {
        ifElse(key);
      }
    }
    long s4 = currentTimeMillis();
    System.out.format("round: %d%n" +
        "switch-string: %d%n" +
        "switch-hash: %d%n" +
        "if-else: %d%n",
      round, s2 - s1,
      s3 - s2, s4 - s3);
  }

  @Benchmark
  public void benchSwitchString() {
    for (String k : KEYS) {
      switchString(k);
    }
  }

  @Benchmark
  public void benchSwitchHash() {
    for (String k : KEYS) {
      switchHash(k);
    }
  }

  @Benchmark
  public void benchIfElse() {
    for (String k : KEYS) {
      ifElse(k);
    }
  }

}