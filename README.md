# Java 7 中 Switch String 探究

### Java 7 中允许对 String 进行 Switch，本文探讨其实现过程；并且构建特殊情况下的测试用例，应该使用 JMH 作为基准测试方法。

本文也是`no zuo no die`的典范。如果不是`zuo`，不会去构建特殊测试用例，无法得到正确的基准测试方法；如果不是`zuo`，为了在手机上显示良好（720p 下每行只能有 38 个字符），不惜把变量弄短，去掉一些修饰符，`import`时使用`*`，缩进使用`2`个字符。

## Switch String 介绍

在 Java 7 之前，`switch`基本只支持`int`及其变种，包括但不限于`char`, `short`, `byte`以及`enum`。在 Java 7 中，允许`switch`处理`String`。

一个典型的可能代码如下：

```java
public class SwitchString {

  static int getWeek(String day) {
    switch (day) {
      case "天":
      case "日":
        return 0;
      default:
        return -1;
    }
  }

}

```

## Switch String 反编译看实现

看起来，是不是很炫？反编译看看。

```java
public class SwitchString {

  static int getWeek(String day) {
    byte var2 = -1;
    switch (day.hashCode()) {
      case 22825:
        if (day.equals("天")) {
          var2 = 0;
        }
        break;
      case 26085:
        if (day.equals("日")) {
          var2 = 1;
        }
    }

    switch (var2) {
      case 0:
      case 1:
        return 0;
      default:
        return -1;
    }
  }

}
```

通过反编译的代码来看，可以明显看到，实际上是先把字符串取`hashCode`，进行第一层`switch`，再直接比较字符串，得到相应的临时值；然后进行第二层`switch`。

如果`hashCode`值不一样，能有效避免字符串比较（当然还是得比较），有性能上的提升；如果`hashCode`值一样，还得继续比较，不如直接的`if-else`。

## 构建字符串让`hashCode`相等

如果字符串的`hashCode`一样，会出现什么样的情况呢？怎样才能让两个字符不一样，但是`hashCode`一样呢？幸好，我们有`hashCode`的源码：

```java

  public int hashCode() {
    int h = hash;
    if (h == 0 && value.length > 0) {
      char val[] = value;
      // 此行略微修改，避免过长
      int s = value.length;

      for (int i = 0; i < s; i++) {
        h = 31 * h + val[i];
      }
      hash = h;
    }
    return h;
  }


```

从源码中，我们可以知道，是把上一个字符的值乘以`31`，然后加上现在的值。所以，我们可以轻易的构建两个字符，只需要满足`a * 31 + b = c * 31 + d`即可。如果更进一步，让`c`与`d`一样，只需要`a * 31 + b = c * 32`。再进一步，令`c`为`a + 1`，那么，`b`为`a + 32`。也就是说，我们任选一个字符`a`，那么`(a)(a+32)`与`(a+1)(a+1)`的`hashCode`就一样了。

根据以上特例，选择`a`为`A`，那么就可知`Aa`与`BB`的`hashCode`值一样。此时，我突然想起孔乙己问过，`回`有几种写法，用一次`回`吧：`囝国`与`回回`的`hashCode`值也一样。

```java
public class SwitchString {

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

}
```

看起来很酷有没有，但是反编译之后，实际上是：

```java
public class SwitchString {

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

}
```

## 特定条件下，`Switch-String`效率不如`if-else`

通过反编译结果，可以看到，`Switch-String`只是语法糖，对传入的值先`hashCode`进行第一次`switch`并比较字符串定位实际值，然后再对实际值进行`switch`得到最终结果。如果`hashCode`一样，和单纯的`if-else`相比，不仅没有少，反而还多了`hashCode`与2次`switch`操作。所以，这种情况下，`Switch-String`应该比`if-else`慢。让我们写个测试用例：

```java

import static java.lang.Integer.*;
import static java.lang.System.*;

public class SwitchString {

  static final String[] KEYS = {
    "AaAa", "AaBB",
    "BBAa", "BBBB",
  };


  // .... 从略

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

}
```

## 测试结果和预期不符

由于现在机器 CPU 都很好，所以我直接运行了 10 亿次，结果出来了：

```
round: 1000000000
switch-string: 23453
switch-hash: 40674
if-else: 46031
```

什么？`Switch-String`居然比`if-else`快，并且，有较大差异？此外，`Switch-String`和`Switch-Hash`相比，只是一个进行了反编译而言，理论应该一致，也居然有这么大的差异？为什么不对呢？

## 使用正确的基准进行测试

由于上面是单纯的运行一段时间，然后执行代码记录时间。实际上，并不是标准的测试过程。所以，先看看`java`推荐什么`benchmark`工具吧，直接搜索`benchmark site:openjdk.java.net`。幸好，有结果：`JMH`。这里不详说`JMH`的使用方法，按照官方文档，创建`maven`工程：

```
shell> mvn ... # 具体语句从略
```

然后，写个简单的测试就好：

```java

import org.openjdk.jmh.annotations.*;

public class SwitchString {

  // ... 从略

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
```

最后，测试结果。默认预热 20 秒，运行 20 秒，跑 10 轮；上面有 3 个测试，要跑 20 分钟。

```
shell> mvn clean install
shell> java -jar target/benchmarks.jar
```

为了便于测试，加进`Switch-Enum`的测试用例，代码大致如下：

```java
public class SwitchString {

  static int switchEnum(String key) {
    AaB a = AaB.valueOf(key);
    switch (a) {
      case AaAa:
      case AaBB:
      case BBAa:
      case BBBB:
        return 1;
      default:
        return -1;
    }
  }

  enum AaB {
    AaAa, AaBB, BBAa, BBBB;
  }

  @Benchmark
  public void benchSwitchEnum() {
    for (String k : KEYS) {
      switchEnum(k);
    }
  }

}
```

最终结果如下（去掉了展示的类名），居然`Switch-Enum`最慢：

```
Benchmark           Mode  Cnt         Score        Error  Units
benchIfElse        thrpt  200  65405833.624 ± 746598.439  ops/s
benchSwitchEnum    thrpt  200  13041096.499 ± 143678.376  ops/s
benchSwitchHash    thrpt  200  24179186.018 ± 523982.268  ops/s
benchSwitchString  thrpt  200  23727556.990 ± 413475.864  ops/s
```

可以明显见到，在此测试用例中，`if-else`比`Switch-String`快，而`Switch-String`与`Switch-Hash`相当，`Switch-Enum`最慢。

## 总结
1. 本文通过反编译方法，得出 Java 7 中引入的`switch`支持`String`只是语法糖，不需要 JVM 额外支持。（所以，也可以在 Android 中使用。）
2. 本文通过阅读`String`的`hashCode`方法，构建`hashCode`值一样的字符串进行特殊测试，比如`Aa`与`BB`的`hashCode`一样，`囝国`与`回回`的`hashCode`一样。
3. 本文通过实践及分析，得出直接循环测试性能的方法是不准确的，引出 Java 官方的微基准测试框架 JMH。

## 致谢
1. [JMH](http://openjdk.java.net/projects/code-tools/jmh/), Java Microbenchmark Harness，Java 官方出品的微基准测试框架
2. [IDEA](https://www.jetbrains.com/idea/)，本文使用 30 天试用版反编译 class 文件
3. [GitHub - switchstring](http://github.com/liudongmiao/switchstring), 本文及演示代码托管站点
4. [Markdown Here](https://github.com/adam-p/markdown-here)，感谢此插件顺利发布到微信公众号，当前需要打[高亮代码中强制插入换行补丁](https://github.com/adam-p/markdown-here/pull/329)
