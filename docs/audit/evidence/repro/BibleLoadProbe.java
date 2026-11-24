package org.biblememory.app;

import android.content.*;
import android.content.res.AssetManager;
import java.util.Arrays;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class BibleLoadProbe {
    @Test public void timings() throws Exception {
        Context c=RuntimeEnvironment.getApplication();
        long[] parses=new long[12];
        for(int i=0;i<parses.length;i++){
            Bible b=new Bible();
            long start=System.nanoTime();
            b.load(c);
            parses[i]=System.nanoTime()-start;
        }
        Arrays.sort(parses);
        System.out.printf("PROBE parse n=12 median=%.3fms%n",(parses[5]+parses[6])/2e6);
        Bible bible=new Bible();
        CountDownLatch entering=new CountDownLatch(1), release=new CountDownLatch(1);
        Context gated=new ContextWrapper(c){
            @Override public AssetManager getAssets(){
                entering.countDown();
                try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("release");}
                catch(InterruptedException e){throw new AssertionError(e);}
                return super.getAssets();
            }
        };
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try{
            Future<?> loading=workers.submit(()->{bible.load(gated);return null;});
            if(!entering.await(5,TimeUnit.SECONDS))throw new AssertionError("entry");
            // Release from a worker 100 ms after the main-thread check starts.
            workers.submit(()->{Thread.sleep(100);release.countDown();return null;});
            long start=System.nanoTime();
            boolean ready=bible.isReady();
            System.out.printf("PROBE main isReady=%.3fms observed=%s%n",(System.nanoTime()-start)/1e6,ready);
            loading.get(5,TimeUnit.SECONDS);
        }finally{release.countDown();workers.shutdownNow();}
    }
}
