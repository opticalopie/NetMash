package cyrus.forest;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.regex.*;

import cyrus.platform.*;
import cyrus.lib.JSON;
import static cyrus.forest.UID.*;

import static cyrus.lib.Utils.*;

/** Persistence of WebObjects.
  * A NoSQL JSON in-memory Database!
  */
public class Persistence implements FileUser {

    // ----------------------------------------

    static public final Charset UTF8 = Charset.forName("UTF-8");

    public  FunctionalObserver funcobs;

    private InputStream      topdbis=null;
    private boolean topdbrd;
    private String directory = null;
    private String db = null;
    private File dbfile=null;

    private JSON cyrusconfig=null;
    private boolean cyrus=false;

    private ConcurrentHashMap<String,CharBuffer> jsoncache = new ConcurrentHashMap<String,CharBuffer>();
    private CopyOnWriteArraySet<WebObject>       syncable  = new CopyOnWriteArraySet<WebObject>();

    // ----------------------------------------

    public Persistence(InputStream topdbis){ try{

        funcobs = FunctionalObserver.funcobs;
        this.topdbis = topdbis;
        this.topdbrd=(topdbis!=null);
        this.directory = Kernel.config.stringPathN("persist:directory");
        this.db        = Kernel.config.stringPathN("persist:db");
        this.dbfile  = new File(directory+"/"+db);

        if(topdbrd) Kernel.readFile(topdbis, this);
        else        Kernel.readFile(dbfile,  this);

        if(topdbrd) compressDB();

        final int syncrate = Kernel.config.intPathN("persist:syncrate");
        new Thread(){ public void run(){ runSync(syncrate); } }.start();

        preload(Kernel.config.listPathN("persist:preload"));
        if(cyrusconfig!=null){
            funcobs.hereIsTheConfigBack(cyrusconfig);
            preload(cyrusconfig.listPathN("persist:preload"));
        }
        log("Persistence: initialised.");

    } catch(Exception e){ log("Persistence: Failure reading DB:"); e.printStackTrace(); return; } }

    boolean isUnix=true;

    public void readable(ByteBuffer bytebuffer, int len){
        if(len == -1) return;
        CharBuffer prevchars=null;
        while(true){
            ByteBuffer jsonbytes=null;
            if(isUnix) jsonbytes = Kernel.chopAtDivider(bytebuffer, "\n\n".getBytes(), true);
            boolean unix=jsonbytes!=null;
            if(!unix)  jsonbytes = Kernel.chopAtDivider(bytebuffer, "\r\n\r\n".getBytes(), true);
            if(jsonbytes==null) return;
            isUnix=unix;
            CharBuffer jsonchars = UTF8.decode(jsonbytes);
            String uid = findUIDAndDetectCyrus(jsonchars);
            if(!isUID(uid)) throw new RuntimeException("Data corrupt:\n"+jsonchars+(prevchars!=null? "Previous object:\n"+prevchars: ""));
            if(uid.equals("cyrusconfig")) cyrusconfig = new JSON(jsonchars,cyrus);
            else jsoncache.put(uid, jsonchars);
            prevchars=jsonchars;
        }
    }

    void preload(LinkedList preloadlist){
        if(preloadlist==null) return;
        for(Object o: preloadlist) funcobs.cachePut(cache(o.toString()));
    }

    public void writable(ByteBuffer bytebuffer, int len){
    }

    // ----------------------------------------

    public WebObject cache(String uid){
        CharBuffer jsonchars = jsoncache.get(uid);
        if(jsonchars==null) return null;
        jsonchars.position(0);
        JSON json = new JSON(jsonchars,cyrus);
        String classname = json.stringPathN("Class"); json.removePath("Class");
        WebObject w=null;
        try{
            if(classname!=null && classname.length() >0){
                w=(WebObject)Class.forName(classname).newInstance();
            } else w=new CyrusLanguage();
            w.construct(json);
            return w;
        }catch(Exception e){
            log("Persistence: Could not build an instance of WebObject ("+uid+" classname="+classname+"):\n"+json);
            e.printStackTrace();
        }
        return null;
    }

    // ----------------------------------------

    public void save(WebObject w){
        if(!w.nopersist) syncable.add(w);
    }

    private void runSync(int syncrate){
        for(int i=0; ; i++){
            if(i%20==0) compressDB();
            try{ for(WebObject w: syncable){
                syncable.remove(w);
                CharBuffer jsonchars;
                synchronized(w){ jsonchars=CharBuffer.wrap(w.toString(cyrus)); }
                jsoncache.put(w.uid, jsonchars);
                    ByteBuffer bytebuffer = UTF8.encode(jsonchars);
                    Kernel.writeFile(dbfile, true, bytebuffer, this);
            }}catch(Exception e){ log("Persistence: Failure writing to DB: "+e.getMessage()); }
            Kernel.sleep(syncrate!=0? syncrate: 100);
        }
    }

    private void compressDB(){ try{
        File ddbfile = new File(directory+"/compressed."+db);
        Kernel.writeFile(ddbfile, false, UTF8.encode(""), this);
        for(Map.Entry<String,CharBuffer> entry: jsoncache.entrySet()){
            CharBuffer jsonchars=entry.getValue();
            jsonchars.position(0);
            ByteBuffer bytebuffer = UTF8.encode(jsonchars);
            Kernel.writeFile(ddbfile, true, bytebuffer, this);
        }
        if(!ddbfile.renameTo(dbfile)) throw new Exception("Compressed snapshot DB rename failed: "+ddbfile+" to "+dbfile);
    }catch(Exception e){ log("Persistence: Failure writing to DB: "+e.getMessage()); } }

    // ----------------------------------------

    static public final String  UIDREJ = "^\\s*\\{\\s*\"UID\":\\s*\"([^\"]+)\".*";
    static public final Pattern UIDPAJ = Pattern.compile(UIDREJ, Pattern.MULTILINE | Pattern.DOTALL);
    static public final String  UIDRES = "^\\s*\\{\\s*UID:\\s*([^\\s]+).*";
    static public final Pattern UIDPAS = Pattern.compile(UIDRES, Pattern.MULTILINE | Pattern.DOTALL);

    public String findUIDAndDetectCyrus(CharBuffer chars){
        Matcher m = UIDPAJ.matcher(chars);
        if(!m.matches()){
                m = UIDPAS.matcher(chars);
                if(!m.matches()) return null;
                cyrus=true;
        }
        return m.group(1);
    }

    // ----------------------------------------
}

