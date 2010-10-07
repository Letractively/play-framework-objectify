package models;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Query;
import controllers.Application;
import play.data.validation.Required;
import play.modules.objectify.Datastore;
import play.modules.objectify.ObjectifyModel;

import javax.persistence.Embedded;
import javax.persistence.Id;
import java.util.List;

/**
 * @author David Cheong
 * @since 3/04/2010
 */
public class Flight extends ObjectifyModel {

    @Id public Long id;
    @Required public String pilot;
    public float price;
    @Required public City origin;
    @Required public City destination;
    public List<City> stopovers;
    @Embedded public Note note = new Note();
    public String owner;

    public String getDisplayString() {
        return origin.label + " - " + destination.label + " (" + pilot + ")";
    }

    public static Flight findById(Long id) {
        return Datastore.find(Flight.class, id, true);
    }

    public static Flight findById(Long id, boolean newIfNull) {
        return Datastore.find(Flight.class, id, newIfNull);
    }

    public static Query<Flight> findAllByOwner() {
        return Datastore
                .query(Flight.class)
                .filter("owner", Application.getUserEmail ())
                .order("pilot");
    }

    public Key<Flight> save() {
        owner = Application.getUserEmail();
        return Datastore.put(this);
    }

    public static void deleteById(Long id) {
        Datastore.beginTxn();
        Passenger.deleteByFlightId(id);
        Datastore.delete(Flight.class, id);
        Datastore.commit();
    }

    public void delete() {
        Datastore.beginTxn();
        Passenger.deleteByFlightId(id);
        Datastore.delete(this);
        Datastore.commit();
    }
}
