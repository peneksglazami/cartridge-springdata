# Spring Data Tarantool

The primary goal of the [Spring Data](https://projects.spring.io/spring-data)
project is to make it easier to build Spring-powered applications that
use new data access technologies such as non-relational databases,
map-reduce frameworks, and cloud based data services.

The Spring Data Tarantool project provides Spring Data way of working
with Tarantool database spaces. The key features are repository support
and different API layers which are common in Spring Data, as well as
support for asynchronous approaches baked by the underlying asynchronous
Tarantool database driver.

## Spring Boot compatibility

|`spring-data-tarantool` Version | Spring Boot Version
| :----------- | :----: |
|1.0.0 | 2.2.x

The Tarantool Database documentation is located at
[tarantool.io](https://www.tarantool.io/en/doc/latest/reference/)

Feel free to join the [Tarantool community chat](https://t.me/tarnatool)
in Telegram (or its counterpart [in Russian](https://t.me/tarnatoolru))
if you have any questions about Tarantool database or Spring Data Tarantool.

Detailed questions can be asked on StackOverslow using the
[tarantool](https://stackoverflow.com/questions/tagged/tarantool) tag.

Documentation and StackOverflow links will be added in the nearest future.

If you are new to Spring as well as to Spring Data, look for information
about [Spring projects](https://projects.spring.io/).

## Quick Start

### Demo project

TBA soon

### Maven configuration

The Tarantool Spring Data connector depends on the new asynchronous
Tarantool driver:

```xml
<dependency>
  <groupId>io.tarantool</groupId>
  <artifactId>driver</artifactId>
</dependency>
```

Download and install it using `mvn install` from
[Github](https://github.com/akudiyar/tarantool-java-driver) (a temporary
measure until the first release is published to Maven Central).

Download and build this project via `mvn install` (a temporary measure
until the first release is published to Maven Central).

Add the Maven dependency:

```xml
<dependency>
  <groupId>org.tarantool</groupId>
  <artifactId>spring-data-tarantool</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### TarantoolTemplate

`TarantoolTemplate` is the central support class for Tarantool database
operations. It provides:

* Basic POJO mapping support to and from Tarantool tuples
* Convenience methods to interact with the store (insert object,
update objects, select)
* Exception translation into Spring's
[technology agnostic DAO exception hierarchy](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/dao.html#dao-exceptions).

### Spring Data repositories

To simplify the creation of data repositories Spring Data Tarantool
provides a generic repository programming model. It will automatically
create a repository proxy for you that adds implementations of finder
methods you specify on an interface.

Only simple CRUD operations including entity ID are suported at the
moment (will be fixed soon).

For example, given a `Book` class with name and author properties, a
`BookRepository` interface can be defined for saving and loading the
entities:

```java
public interface BookRepository extends CrudRepository<Person, Long> {
}
```

Extending `CrudRepository` causes CRUD methods being pulled into the
interface so that you can easily save and find single entities and
collections of them.

You can have Spring automatically create a proxy for the interface
by using the following JavaConfig:

```java
@Configuration
@EnableTarantoolRepositories(basePackageClasses = PersonRepository.class)
class ApplicationConfig extends AbstractTarantoolDataConfiguration {

	@Override
    protected TarantoolServerAddress tarantoolServerAdress() {
    	return new TarantoolServerAddress("localhost", 3301);
    }
}
```

This sets up a connection to a local Tarantool instance and enables the
detection of Spring Data repositories (through `@EnableTarantoolRepositories`).

This will find the repository interface and register a proxy object in the container. You can use it as shown below:

```java
@Service
public class MyService {

  private final PersonRepository repository;

  @Autowired
  public MyService(PersonRepository repository) {
    this.repository = repository;
  }

  public void doWork() {
    Person person = new Person();
    person.setFirstname("Alexey");
    person.setLastname("Kuzin");
    person = repository.save(person);

    List<Person> allPersons = repository.findAll();
  }
}
```

## Contributing to Spring Data Tarantool

Contributions and issues are welcome, feel free to add them to this
project or offer directly in the Tarantool community chat or on
StackOverflow using the [tarantool](https://stackoverflow.com/questions/tagged/tarantool)
tag.
