package controllers;

import play.modules.gae.GAE;
import play.mvc.*;

public class Application extends Controller {

    public static void index() {
        Secure.renderLoggedInUser();
        render();
    }

    public static void login() {
        GAE.login("Application.index");
    }

    public static void logout() {
        GAE.logout("Application.index");
    }

}