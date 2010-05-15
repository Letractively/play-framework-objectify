package models;

import com.google.appengine.api.datastore.QueryResultIterable;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.annotation.Parent;
import play.data.validation.Required;
import play.modules.gae.GAE;
import play.modules.objectify.Datastore;
import play.modules.objectify.ObjectifyModel;

import javax.persistence.Id;

/**
 * @author David Cheong
 * @since 3/04/2010
 */
public class Passenger extends ObjectifyModel {

    @Id public Long id;
    @Required public String firstName;
    @Required public String lastName;
    @Required @Parent public Key<Flight> flight;
    public String owner;

    public static Passenger findById(Long flightId, Long id) {
        Key<Passenger> key = Datastore.key(Flight.class, flightId, Passenger.class, id);
        return Datastore.find(key, true);
    }

    public static Query<Passenger> findAllByOwner() {
        return Datastore
                .query(Passenger.class)
                .filter("owner", GAE.getUser().getEmail())
                .order("lastName");
    }

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

    public Key<Passenger> save() {
        owner = GAE.getUser().getEmail();
        return Datastore.put(this);
    }

    public static void deleteByFlightId(Long flightId) {
        // todo slow if many - how to delete in bulk?
        QueryResultIterable<Key<Passenger>> passengers = Datastore.query(Passenger.class)
                .ancestor(Datastore.key(Flight.class, flightId))
                .fetchKeys();
        Datastore.delete(passengers);
    }

    public void delete() {
        Datastore.delete(this);
    }

}