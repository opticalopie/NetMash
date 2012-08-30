package server.types;

import java.util.*;
import netmash.forest.Editable;

/** Tracks Presence of Users viewing it.
  */
public class PresenceTracker extends Editable {

    public PresenceTracker(){}

    public void evaluate(){
        if(contentListContains("is", "3d")){
            trackPresence();
        }
        super.evaluate();
    }

    @SuppressWarnings("unchecked")
    private void trackPresence(){ logrule();

        LinkedList subuids=contentAll("subObjects:object");
        for(String alerted: alerted()){
            contentTemp("%alerted", alerted);
            if(contentIsThis("%alerted:place") && !subuids.contains(alerted)){
                LinkedHashMap hm=new LinkedHashMap();
                hm.put("object", alerted);
                hm.put("coords", list(0,0,0));
                contentListAdd("subObjects", hm);
                contentInc("present");
            }
            contentTemp("%alerted", null);
        }
        LinkedList subObjects=contentList("subObjects");
        if(subObjects==null) return;
        int sosize=subObjects.size();
        for(int i=0; i<sosize; i++){
            String placepath=String.format("subObjects:%d:object:place",i);
            if(!contentSet(placepath)) continue;
            if(contentIsThis(placepath)){
                String coordpath=String.format("subObjects:%d:object:coords",i);
                String mycrdpath=String.format("subObjects:%d:coords",i);
                LinkedList coords=contentListClone(coordpath);
                if(coords!=null && coords.size()==3) contentList(mycrdpath, coords);
            }
            else{
                contentListRemove("subObjects", i);
                contentDec("present");
                i--; sosize--;
            }
        }
    }
}

