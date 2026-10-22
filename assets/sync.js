/* ES5 timing policy: fresh TV observations drive bounded corrections. */
var M13Sync={plan:function(tv,local,now){
 if(!tv||!tv.known||!tv.active||!local.ready||!isFinite(local.time))return null;
 var age=Math.max(0,Number(tv.ageMs)||0)+Math.max(0,now-tv.receivedAt)+(Number(tv.networkMs)||0)/2;
 if(age>6000||!isFinite(tv.positionMs))return null;
 if(isFinite(local.duration)&&local.duration>0&&tv.durationMs>0&&Math.abs(local.duration-tv.durationMs/1000)>10)return null;
 var target=tv.positionMs/1000+(tv.playing?age/1000:0);
 if(isFinite(local.duration)&&local.duration>0)target=Math.max(0,Math.min(target,local.duration-.25));
 var difference=target-local.time;
 return {target:target,difference:difference,playing:!!tv.playing,seek:!local.seeking&&Math.abs(difference)>1.25&&now-local.lastSeekAt>=5500};
}};
