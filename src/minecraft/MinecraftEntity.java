package net.minecraft.src;

import java.util.*;
import java.util.concurrent.*;

import cyrus.forest.CyrusLanguage;
import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.client.Minecraft;

public class MinecraftEntity extends CyrusLanguage implements mod_Cyrus.Tickable {

    public MinecraftEntity(){}

    Entity entity;

    public MinecraftEntity(Entity e, String type, String name, String worlduid){
        super("{ \"is\": [ \"editable\", \"3d\", \"minecraft\", \""+type+"\", \"entity\" ],\n"+
              "  \"name\": \""+name+"\",\n"+
              "  \"world\": \""+worlduid+"\"\n"+
              "}");
        entity=e;
    }

    boolean running=false;

    public void evaluate(){
        super.evaluate();
        if(!running){ running=true; mod_Cyrus.modCyrus.registerTicks(this); }
    }

    private double x=30000000,y=30000000,z=30000000;

    public void tick(float var1, Minecraft minecraft){
        double px=entity.posX;
        double py=entity.posY;
        double pz=entity.posZ;
        if(vvdist(list(x,y,z),list(px,py,pz)) >= 1){
            x=px; y=py; z=pz;
            new Evaluator(this){ public void evaluate(){ try{
                contentList("position",list((int)(x-1.0),(int)(y-2.5),(int)(z-1.0)));
                self.evaluate();
            }catch(Exception e){ e.printStackTrace(); } refreshObserves(); }};
        }
    }
}

