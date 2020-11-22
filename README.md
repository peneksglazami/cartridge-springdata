# Spring Data Tarantool

[![cartridge-springdata:ubuntu/master Actions Status](https://github.com/tarantool/cartridge-springdata/workflows/ubuntu-master/badge.svg)](https://github.com/tarantool/cartridge-springdata/actions)

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
|0.x.x | 2.2.x

## Tarantool compatibility

|`spring-data-tarantool` Version | Tarantool Version
| :----------- | :----: |
| 0.x.x | 1.10.x, 2.x

## References

The Tarantool Database documentation is located at
[tarantool.io](https://www.tarantool.io/en/doc/latest/reference/)

Feel free to join the [Tarantool community chat](https://t.me/tarnatool)
in Telegram (or its counterpart [in Russian](https://t.me/tarnatoolru))
if you have any questions about Tarantool database or Spring Data Tarantool.

Detailed questions can be asked on StackOverflow using the
[tarantool](https://stackoverflow.com/questions/tagged/tarantool) tag.

Documentation and StackOverflow links will be added in the nearest future.

If you are new to Spring as well as to Spring Data, look for information
about [Spring projects](https://projects.spring.io/).

## Quick Start

### Demo project

TBA soon

### Maven configuration

Add the Maven dependency:

```xml
<dependency>
  <groupId>io.tarantool</groupId>
  <artifactId>spring-data-tarantool</artifactId>
  <version>0.3.0</version>
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

Only simple CRUD operations including entity ID are supported at the
moment (will be fixed soon).

For example, given a `Book` class with name and author properties, a
`BookRepository` interface can be defined for saving and loading the
entities:

```java
public interface BookRepository extends TarantoolRepository<Book, Long> {
}
```

Extending `CrudRepository` causes CRUD methods being pulled into the
interface so that you can easily save and find single entities and
collections of them.

Let's assume that you have the [tarantool/crud](https://github.com/tarantool/crud) module installed in yor Cartridge
application and there is an active 'crud-router' role on your router. Put the following settings in your application
properties:

| Property name      | Example value | Description
| :----------------  | :-----------  | :---------
| tarantool.host     | localhost            | Cartridge router host
| tarantool.port     | 3301                 | Cartridge router port
| tarantool.username | admin                | Default username for API access, may be different
| tarantool.password | myapp-cluster-cookie | Password for API access, see you Cartridge application configuration

Than you can have Spring automatically create a proxy for the repository interface by using the following JavaConfig:

```java
@Configuration
@EnableTarantoolRepositories(basePackageClasses = BookRepository.class)
class ApplicationConfig extends AbstractTarantoolDataConfiguration {

    @Value("${tarantool.host}")
    protected String host;
    @Value("${tarantool.port}")
    protected int port;
    @Value("${tarantool.username}")
    protected String username;
    @Value("${tarantool.password}")
    protected String password;

    @Override
    protected TarantoolServerAddress tarantoolServerAddress() {
        return new TarantoolServerAddress(host, port);
    }

    @Override
    public TarantoolCredentials tarantoolCredentials() {
        return new SimpleTarantoolCredentials(username, password);
    }

    @Override
    public TarantoolClient tarantoolClient(TarantoolClientConfig tarantoolClientConfig,
                                           TarantoolClusterAddressProvider tarantoolClusterAddressProvider) {
        return new ProxyTarantoolClient(super.tarantoolClient(tarantoolClientConfig, tarantoolClusterAddressProvider));
    }
}
```

This sets up a connection to a local Tarantool instance and enables the
detection of Spring Data repositories (through `@EnableTarantoolRepositories`).

This will find the repository interface and register a proxy object in the container. You can use it as shown below:

```java
@Service
public class MyService {

  private final BookRepository repository;

  @Autowired
  public MyService(BookRepository repository) {
    this.repository = repository;
  }

  public void doWork() {
    Book book = new Book();
    book.setName("Le Petit Prince");
    book.setAuthor("Antoine de Saint-Exupéry");
    book = repository.save(book);

    List<Book> allBooks = repository.findAll();
  }
}
```

#### Proxy methods in repositories

Consider we need to write a complex query in Lua, working with sharded data in Tarantool Cartridge. In this case
we can expose this query as a public API function and map that function on a repository method via the `@Query`
annotation:

```java
public interface BookRepository extends CrudRepository<Book, Long> {
    @Query(function = "find_by_complex_query")
    List<Book> findByYearGreaterThenProxy(Integer year);
}
```

The corresponding function in on Tarantool Cartridge router may look like (uses the
[tarantool/crud](https://github.com/tarantool/crud) module):

```lua
    local crud = require('crud')
    local fun = require('fun')

    ...

    function find_by_complex_query(year)
        return fun.iter(crud.pairs('books')):filter(function(b) return b[6] and b[6] > year end):totable()
    end
```

## Contributing to Spring Data Tarantool

Contributions and issues are welcome, feel free to add them to this
project or offer directly in the Tarantool community chat or on
StackOverflow using the [tarantool](https://stackoverflow.com/questions/tagged/tarantool)
tag.

