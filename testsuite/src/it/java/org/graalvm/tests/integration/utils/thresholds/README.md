# Thresholds properties

We need to switch on and off certain tests depending on native-image versions used,
on host vs. container and most importantly, based on Quarkus version.

We use method annotations for that, e.g. `@IfQuarkusVersion(min = "3.6.0")`,
or `@IfMandrelVersion(min = "23.0.0", inContainer = true)`.

We also use properties in text files to validate whether particular thresholds were crossed, e.g. whether the app used more RAM than expected or whether the measured time delta between JVM HotSpot mode and native-image mode was bigger than expected.

e.g.

```
linux.jvm.time.to.finish.threshold.ms=6924
linux.native.time.to.finish.threshold.ms=14525
```

The current `.conf` format enhances `.properties` format with the power of using the 
annotation strings, see:

```
# Comments and empty lines are ignored
linux.jvm.time.to.finish.threshold.ms=6000
linux.native.time.to.finish.threshold.ms=14000
linux.executable.size.threshold.kB=79000

@IfQuarkusVersion(min ="2.7.0", max="3.0.0")
linux.jvm.time.to.finish.threshold.ms=5000
linux.native.time.to.finish.threshold.ms=13000
linux.executable.size.threshold.kB=75000

@IfQuarkusVersion(min ="3.5.0", max="3.5.999")
@IfMandrelVersion(min = "23.1.2", minJDK = "21.0.1" )
linux.jvm.time.to.finish.threshold.ms=6924
linux.native.time.to.finish.threshold.ms=14525
linux.executable.size.threshold.kB=79000

@IfQuarkusVersion(min ="3.6.0")
linux.jvm.time.to.finish.threshold.ms=6924
linux.native.time.to.finish.threshold.ms=14525
linux.executable.size.threshold.kB=79000

@IfMandrelVersion(min = "24", minJDK = "21.0.1" )
linux.executable.size.threshold.kB=90000
```

Properties are being added to a map top to bottom, overwriting their previous values
unless an `@If` constraint fails. If a condition fails, the following properties are
not added to the map until the next `@If` constraint is met.

If two `@If` constraints follow immediately one after the other, they both MUST be true
to process the following properties.

Take a look at [ThresholdsTest.java](./ThresholdsTest.java) and its `threshold-*.conf` test [files](../../../../../../../../test/resources/) for a comprehensive overview.

The parsing logic is compatible with plain `.properties` files as we have been using before,
i.e. any key-value pair where the value is interpreted as the long type.
