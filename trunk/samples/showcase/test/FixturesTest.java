import com.google.appengine.api.datastore.QueryResultIterable;
import models.Flight;
import org.junit.Before;
import org.junit.Test;
import play.modules.objectify.Datastore;
import play.modules.objectify.ObjectifyFixtures;
import play.test.UnitTest;

/**
 * @author David Cheong
 * @since 14/10/2010
 */
public class FixturesTest extends UnitTest {

    @Before
    public void setup() {
//        ObjectifyFixtures.deleteAll();
    }

    @Test
    public void should_load_testdata() {
        ObjectifyFixtures.load("_testdata.yml");
        QueryResultIterable<Flight> itr = Datastore.query(Flight.class).fetch();
        for (Flight flight : itr) {
            System.out.println("flight = " + flight);
        }
    }

}
