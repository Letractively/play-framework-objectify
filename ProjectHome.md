# Play Framework Module for Objectify on Google App Engine/J #

The Play Framework is an application framework which makes web development in Java easy and rapid. Objectify is a flexible abstraction on Google App Engine/J which makes data access simple and elegant. Together the Play Framework and Objectify provide a powerful technology stack for building fast and scalable applications on Google's infrastructure.

This Play Framework Module makes it possible for these two technologies to work together cohesively. This plugin:

  * Provides a HTTP binder to work with Key, Lists, Sets and arbitrary data structures
  * Provides helpers for doing data access in any class or subclasses of `ObjectifyModel`
  * Enables simple handling of one or many transactions
  * Enhances the Key construct so it can auto fetch inside Templates
  * Exposes Objectify utility methods on the Template

The sample showcase application available in this plugin is deployed to http://play-framework-objectify.appspot.com/.

## Prerequisite ##

Before looking at this plugin, the reader is assumed to be familiar with the Play Framework and the Objectify library. If not, please refer to the respective homepages:

  * http://www.playframework.org/
  * http://code.google.com/p/objectify-appengine/

## Installation ##

Please install this plugin via the official Play repository:

http://www.playframework.org/modules/objectify

In `application.conf` add the GAE plugin and this plugin to your application:

```
module.gae=${play.path}/modules/gae-1.0.2
module.objectify=${play.path}/modules/objectify
```

## Modelling Entities ##

In this plugin, a persistent entity class must satisfy two conditions:

  * Extends `ObjectifyModel`
  * Configured in `application.conf` via the property "`objectify.models`"

Let's consider the case of two entities `Flight` and `Passenger`, with `Flight` being the parent of `Passenger`.

Their class definitions might look like:

```
public class Flight extends ObjectifyModel {
    @Id public Long id;
    @Required public String pilot;
    public String owner;
}
```

```
public class Passenger extends ObjectifyModel {
    @Id public Long id;
    @Required @Parent public Key<Flight> flight;
    @Required public String firstName;
    @Required public String lastName;
    public String owner;
}
```

Their entries in `application.conf` would be:

```
objectify.models=models.Flight,models.Passenger
```

or simply:

```
objectify.models=Flight,Passenger
```

## `Datastore`, `ObjectifyService`, `ObjectifyFactory` ##

This plugin provides a simple set of utilities and convenience methods to work with Objectify. It does not attempt to abstract away either Objectify or the Datastore completely, as it would be pointless. Instead, it focuses on making things easier for application development.

The central class applications would use is the `Datastore`. This class provides access to queries, transactions and operations to save and delete entities. It works in conjunction with `ObjectifyService` and `ObjectifyFactory` to do this.

The best way to describe the `Datastore` is by illustrative examples.

**Example: Find an entity by its Long id**

```
    public static Flight findById(Long id) {
        return Datastore.find(Flight.class, id, true);
    }
```

or

```
    public static Flight findById(Key<Flight> key) {
        return Datastore.find(key, true);
    }
```

Most finder methods in `Datastore` are heavily overloaded in order to be flexible. Also, in the example above, the last parameter is a flag to indicate if the `Datastore` should return a new blank instance of the specified entity if not found or simply return null.

**Example: Find a child entity by its id**

An Objectify `Key` (and also a native GAE Key) require information about the entire path of an entity in order to retrieve it. Objectify let's you construct a `Key` by nesting parent `Key`s. For a `Passenger` whose parent is `Flight` you would need to:

```
    public static Passenger findById(Long flightId, Long passengerId) {
        Key<Flight> flightKey = new Key<Flight>(Flight.class, flightId);
        Key<Passenger> passengerKey = new Key<Passenger>(flightKey, Passenger.class, passengerId);
        return Datastore.find(passengerKey, true);
    }
```

This plugin let's you write this more succinctly as:

```
    public static Passenger findById(Long flightId, Long passengerId) {
        Key<Passenger> key = Datastore.key(Flight.class, flightId, Passenger.class, passengerId);
        return Datastore.find(key, true);
    }
```

**Example: Search for entities belonging to the logged in user**

```
    public static Query<Flight> findAllByOwner() {
        return Datastore
                .query(Flight.class)
                .filter("owner", GAE.getUser().getEmail())
                .order("pilot");
    }
```

**Example: Search for entities belonging to an ancestor**

```
    public static Query<Passenger> findByFlightId(Long flightId) {
        if (flightId != null) {
            return Datastore.query(Passenger.class)
                    .ancestor(Datastore.key(Flight.class, flightId))
                    .order("lastName");
        }
        else {
            return findAllByOwner();
        }
    }
```

**Example: Save an entity**

```
    public Key<Passenger> save() {
        owner = GAE.getUser().getEmail();
        return Datastore.put(this);
    }
```

**Example: Delete an entity**

```
    public void delete() {
        Datastore.delete(this);
    }
```

## Transactions ##

Objectify lets you create as many `Objectify` instances in a request you need, each either being non-transactional or having their own transactions. By default when the application handles a request, an `Objectify` instance is created without transaction for use throughout the request. In order to keep things simple, this plugin adopts a "stack" based approach to managing multiple `Objectify` instances. Instances are added to the stack every time you call `begin()` or `beginTxn()` and removed every time you call `commit()` or `rollback()`.

**Example: Perform multiple operations within a transaction**

```
    public void delete() {
        Datastore.beginTxn();
        Passenger.deleteByFlightId(id);
        Datastore.delete(this);
        Datastore.commit();
    }
```

In the example above, a new `Objectify` instance was added to the stack when `beginTxn()` was called. It was then removed from the stack when `commit()` was called. All `Datastore` operations between these two statements operated within a single transaction.

For added convenience, it is not necessary to call `commit()`, the plugin will ensure all opened transactions are committed before it finishes handling a request.

## Template enhancements ##

Objectify strongly encourages the use of `Key`s to represent relationships vs mapping relationships as real entities. This gives the application developer the best control and flexibility, which balances code complexity and application performance/scalability.

This is all good, but when given a `Key` reference whilst rendering a view, it makes it difficult to traverse the entity relationships. To alleviate this pain point, this plugin augments the humble `Key` class via a Play `JavaExtensions` which allows a `Key` to auto-inflate in a template via the `fetch()` method.

**Example: Traverse a nested `Key` relationship**

```
Passenger First Name is ${passenger.firstName}<br/>
Passenger Last Name is ${passenger.lastName}<br/>
Passenger Flight Pilot is ${passenger.flight.fetch().pilot}<br/>
```

When saving entities, it is sometimes necessary to save a `Key` for a relationship. This is also easy via the `str()` method.

**Example: Saving a reference to an entity via its Key**

```
#{field 'passenger.flight'}
    <tr class="field ${field.errorClass}">
        <td class="label"><label for="flight">Flight:</label></td>
        <td class="field">
            <select id="flight" name="${field.name}">
                <option value="" ${passenger.flight == null ? 'selected' : ''}>Not specified</option>
                #{list flights, as:'flight'}
                <option value="${flight.str()}" ${passenger.flight?.id == flight.id ? 'selected' : ''}>${flight.displayString}</option>
                #{/list}
            </select>
        </td>
        <td>#{ifError field.name}<span class="error">${field.error}</span>#{/ifError}</td>
    </tr>
#{/field}
```

## Support ##

This code is provided as is without warranty. If you encounter a bug or have a change request, please add it to:

http://code.google.com/p/play-framework-objectify/issues/list

You may also find it helpful to post to the Play Google Groups:

http://groups.google.com/group/play-framework




