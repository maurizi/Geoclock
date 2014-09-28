[![Travis build status](https://travis-ci.org/maurizi/Geoclock.png?branch=master)](https://travis-ci.org/maurizi/Geoclock)
[![Coverage Status](https://coveralls.io/repos/maurizi/Geoclock/badge.png?branch=master)](https://coveralls.io/r/maurizi/Geoclock?branch=master)

Geoclock
========

An Android app to change your alarm clock settings based on your location

Development
-----------
Uses [gradle-retrolambda](https://github.com/evant/gradle-retrolambda) in order to support Java 8 lambdas on Android.  This requires Java 8 in order to build, and if you are building outside of Android Studio the `JAVA8_HOME` environment variable must be set to the location of the Java 8 JDK.

#### Android Studio
 - Requires [a plugin](https://github.com/evant/android-studio-unit-test-plugin) to run unit tests from Android Studio.
 - Uses [Project Lombok](http://projectlombok.org/) annotations, which requires [a plugin](http://plugins.jetbrains.com/plugin/6317) for code completion to work properly.
