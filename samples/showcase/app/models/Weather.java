package models;

import play.data.validation.Required;
import play.modules.objectify.ObjectifyModel;

import javax.persistence.Embedded;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * @author David Cheong
 * @since 09/10/2010
 */
public class Weather extends ObjectifyModel {

    @Id @GeneratedValue public Long id;
    @Required public String description;
    @Required public City city;
    public int temperature;
    @Embedded public Note note = new Note();

}
