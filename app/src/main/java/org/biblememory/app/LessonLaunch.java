package org.biblememory.app;

import android.content.SharedPreferences;

final class LessonLaunch {
    record GroupDraft(GroupLearningLogic.Candidate candidate,int returnCursor) {}
    private LessonLaunch(){}

    static LocalStore.Entry select(LocalStore store,LessonSessionState lesson,long id){
        LocalStore.Entry source=store.entry(id);
        lesson.lessonSourceEntry=id;lesson.lessonSourceGroup=source==null?-1:source.groupId;
        lesson.selectedEntry=store.canonicalStudyEntryId(id);
        id=lesson.selectedEntry;
        LocalStore.Entry entry=store.entry(id);
        if(entry==null)return null;
        lesson.lessonSessionMaterial=LessonCheckpoint.material(entry);
        lesson.lessonSourceMaterial=source==null||source.id==entry.id?"":LessonCheckpoint.material(source);
        return entry;
    }

    static GroupDraft pendingGroup(LocalStore store,SharedPreferences saved,LessonSessionState lesson,LocalStore.Entry entry){
        String key="linked_"+entry.id+"_";
        if(!saved.getBoolean(key+"pending",false))return null;
        int start=saved.getInt(key+"begin",0),end=saved.getInt(key+"end",0);
        String stage=saved.getString(key+"group_stage","CHAIN");
        if(start>=0&&end>start&&end<=entry.lines().size()&&!store.isStepMastered(entry.id,stage,start,end)
            &&sameDraft(lesson,saved,key)){
            return new GroupDraft(GroupLearningLogic.candidate(stage,start,end,"Продолжить связку"),
                saved.getInt(key+"return_cursor",0));
        }
        saved.edit().remove(key+"pending").apply();
        return null;
    }

    static boolean sameDraft(LessonSessionState lesson,SharedPreferences preferences,String key){
        return lesson.lessonSessionMaterial.equals(preferences.getString(key+"lesson_material",lesson.lessonSessionMaterial));
    }
}
