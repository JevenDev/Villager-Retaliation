package com.jvn.villagerretaliation.scene.runtime;

public record SceneStepResult(Outcome outcome,long wakeTime,String code,String diagnostic,String transition) {
    public SceneStepResult { outcome=outcome==null?Outcome.BLOCK:outcome;code=code==null?"":code;diagnostic=diagnostic==null?"":diagnostic;transition=transition==null?"":transition; }
    public static SceneStepResult ready(){return new SceneStepResult(Outcome.READY,0,"","","");}
    public static SceneStepResult applied(){return new SceneStepResult(Outcome.APPLIED,0,"","","");}
    public static SceneStepResult complete(){return new SceneStepResult(Outcome.COMPLETE,0,"","","");}
    public static SceneStepResult complete(String transition){return new SceneStepResult(Outcome.COMPLETE,0,"","",transition);}
    public static SceneStepResult waitUntil(long time,String diagnostic){return new SceneStepResult(Outcome.WAIT,time,"",diagnostic,"");}
    public static SceneStepResult block(String code,String diagnostic){return new SceneStepResult(Outcome.BLOCK,0,code,diagnostic,"");}
    public static SceneStepResult fail(String code,String diagnostic){return new SceneStepResult(Outcome.FAIL,0,code,diagnostic,"");}
    public static SceneStepResult skip(){return new SceneStepResult(Outcome.SKIP,0,"","","");}
    public enum Outcome{READY,APPLIED,COMPLETE,WAIT,BLOCK,FAIL,SKIP}
}
