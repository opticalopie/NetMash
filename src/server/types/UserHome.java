package server.types;

import cyrus.forest.WebObject;

/** User Home collection.
  */
public class UserHome extends WebObject {

    public UserHome(){}

    public void evaluate(){
        if(contentListContains("is", "userhome")){
            addNewUsers();
        }
    }

    private void addNewUsers(){
        for(String useruid: alerted()){
            contentSetAdd("list", useruid);
        }
    }
}

