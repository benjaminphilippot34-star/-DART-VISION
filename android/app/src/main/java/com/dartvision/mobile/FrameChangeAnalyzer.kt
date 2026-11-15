package com.dartvision.mobile
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
data class ChangedRegion(val minX:Int,val minY:Int,val maxX:Int,val maxY:Int,val pixelCount:Int,val centroidX:Double,val centroidY:Double)
/** Compare des empreintes de luminance. Une région n'est pas une détection de fléchette ni de pointe. */
class FrameChangeAnalyzer(private val sampleWidth:Int=160,private val sampleHeight:Int=160,private val pixelThreshold:Int=28,private val minRegionPixels:Int=3):ImageAnalysis.Analyzer {
 private val latestFrame=AtomicReference<ByteArray?>(null)
 @Volatile private var referenceFrame:ByteArray?=null
 @Volatile var changedPercent:Double?=null; private set
 @Volatile var changedRegions:List<ChangedRegion> = emptyList(); private set
 @Volatile var framesReceived:Long=0; private set
 @Volatile var imageWidth:Int=0; private set
 @Volatile var imageHeight:Int=0; private set
 @Volatile var rotationDegrees:Int=0; private set
 init { require(sampleWidth>0&&sampleHeight>0);require(pixelThreshold in 1..255);require(minRegionPixels>=1) }
 override fun analyze(image:ImageProxy){try{imageWidth=image.width;imageHeight=image.height;rotationDegrees=image.imageInfo.rotationDegrees;val p=image.planes.firstOrNull()?:return;val b=p.buffer;val d=ByteArray(b.remaining());b.get(d);val s=sampleLuma(d,image.width,image.height,p.rowStride,p.pixelStride,sampleWidth,sampleHeight);latestFrame.set(s);framesReceived++;referenceFrame?.let{changedPercent=changedPixelPercent(it,s,pixelThreshold);changedRegions=detectChangedRegions(it,s,sampleWidth,sampleHeight,pixelThreshold,minRegionPixels)}}finally{image.close()}}
 fun captureReference():Boolean{val c=latestFrame.get()?:return false;referenceFrame=c.copyOf();changedPercent=0.0;changedRegions=emptyList();return true}
 fun clearReference(){referenceFrame=null;changedPercent=null;changedRegions=emptyList()}
 fun hasReference()=referenceFrame!=null
 companion object {
  internal fun sampleLuma(data:ByteArray,imageWidth:Int,imageHeight:Int,rowStride:Int,pixelStride:Int,outWidth:Int,outHeight:Int):ByteArray{require(imageWidth>0&&imageHeight>0&&rowStride>0&&pixelStride>0&&outWidth>0&&outHeight>0);val o=ByteArray(outWidth*outHeight);for(y in 0 until outHeight)for(x in 0 until outWidth){val sy=(y.toLong()*imageHeight/outHeight).toInt().coerceAtMost(imageHeight-1);val sx=(x.toLong()*imageWidth/outWidth).toInt().coerceAtMost(imageWidth-1);val i=sy*rowStride+sx*pixelStride;o[y*outWidth+x]=if(i in data.indices)data[i]else 0};return o}
  internal fun changedPixelPercent(reference:ByteArray,current:ByteArray,threshold:Int):Double{require(reference.size==current.size);if(reference.isEmpty())return 0.0;var n=0;for(i in reference.indices)if(abs((reference[i].toInt() and 255)-(current[i].toInt() and 255))>=threshold)n++;return n*100.0/reference.size}
  internal fun detectChangedRegions(reference:ByteArray,current:ByteArray,width:Int,height:Int,threshold:Int,minPixels:Int=3):List<ChangedRegion>{require(width>0&&height>0&&reference.size==width*height&&current.size==width*height);require(threshold in 1..255&&minPixels>=1);val seen=BooleanArray(reference.size);val q=IntArray(reference.size);val out=mutableListOf<ChangedRegion>();for(start in reference.indices){if(seen[start]||abs((reference[start].toInt() and 255)-(current[start].toInt() and 255))<threshold)continue;var h=0;var t=0;q[t++]=start;seen[start]=true;var x0=width;var y0=height;var x1=0;var y1=0;var sx=0L;var sy=0L;while(h<t){val i=q[h++];val x=i%width;val y=i/width;x0=minOf(x0,x);x1=maxOf(x1,x);y0=minOf(y0,y);y1=maxOf(y1,y);sx+=x;sy+=y;val ns=intArrayOf(if(x>0)i-1 else -1,if(x<width-1)i+1 else -1,if(y>0)i-width else -1,if(y<height-1)i+width else -1);for(j in ns)if(j>=0&&!seen[j]&&abs((reference[j].toInt() and 255)-(current[j].toInt() and 255))>=threshold){seen[j]=true;q[t++]=j}};if(t>=minPixels)out+=ChangedRegion(x0,y0,x1,y1,t,sx.toDouble()/t,sy.toDouble()/t)};return out.sortedByDescending{it.pixelCount}}
 }
}