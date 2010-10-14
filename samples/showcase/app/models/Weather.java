package models;

import com.googlecode.objectify.Key;
import play.data.validation.Required;
import play.modules.objectify.ObjectifyModel;

import javax.persistence.Embedded;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Date;
import java.util.List;

/**
 * @author David Cheong
 * @since 09/10/2010
 */
public class Weather extends ObjectifyModel<Weather> {

    @Id @GeneratedValue public Long id;
    @Required public Date date;
    @Required public String description;
    @Required public City city;
    @Required public int temperature;
    public boolean safeToFly;
    @Embedded public Note note = new Note();
    public List<Key<Flight>> affectedFlights;

}
