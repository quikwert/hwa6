# Homework 6

This project is configured to work offline using local libraries in the `lib/` directory. All code resides in the default package.

## 📂 Project Structure

```
hwa6/
├── src/main/java/       # Application source code
│   └── GraphTask.java      # Main class with lab exercises
├── src/test/java/       # Test code
│   ├── GraphTaskTest.java  # JUnit tests
│   └── Aout.java        # Test helper utilities
├── lib/                 # Local libraries
│   ├── junit-4.13.2.jar
│   └── hamcrest-core-1.3.jar
├── bin/                 # Compiled class files
└──
```

## 🛠️ Command Line Instructions

Since the project uses the default package and local JAR files, commands differ by operating system (path separator: Windows `;` vs Linux/Mac `:`).

### Windows (Command Prompt / PowerShell)

```bash
# 1. Compile main code
javac -d bin src/main/java/*.java

# 2. Compile tests (requires lib folder and main code in bin)
javac -d bin -cp "lib/*;bin" src/test/java/*.java

# 3. Run application
java -cp bin GraphTask

# 4. Run JUnit tests
java -cp "bin;lib/*" org.junit.runner.JUnitCore GraphTaskTest
```

### Linux and macOS

```bash
# 1. Compile main code
javac -d bin src/main/java/*.java

# 2. Compile tests (requires lib folder and main code in bin)
javac -d bin -cp "lib/*:bin" src/test/java/*.java

# 3. Run application
java -cp bin GraphTask

# 4. Run JUnit tests
java -cp "bin:lib/*" org.junit.runner.JUnitCore GraphTaskTest
```

---


## 📋 Task Description

Graph problems are individual for each student and junit-testing is therefore impossible. You must still submit your program text in Moodle, also you find here the pointer structure description (classes Graph, Vertex and Arc) to present graphs. To receive a task you have to visit the lab.

Do not forget that you also have to write a report on your solution and upload it as a pdf-file in Moodle.
---

## ⚙️ Requirements

- **Java 8** or higher
