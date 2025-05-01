## Performance guidelines
* When generating UUID, always use [UUIDv7](../../gimlee-common/src/main/kotlin/com/gimlee/common/UUIDv7.kt), unless
  there's no chance somebody will create any kind of index on an attribute of this type
* Each client of any kind should be pooled; the pools should be clearly defined and configured
  (the pool sizes, idle timeouts etc. should all be configurable via properties)
* Each custom thread pool of any kind should be configurable via properties.
* When you feel that something might be worth caching, do it—preferably using caffeine
  and Kryo library. IMPORTANT: Do not use `@Cacheable` annotation; these are for 
  inferior beings. Each cache should be explicitly set up via application properties, making it easy to see
  what kind of JVM resources might be used by certain cache.