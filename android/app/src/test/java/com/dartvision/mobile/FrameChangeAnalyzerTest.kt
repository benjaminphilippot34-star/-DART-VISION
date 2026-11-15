package com.dartvision.mobile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class FrameChangeAnalyzerTest {
 @Test fun identicalFramesHaveNoChangedPixels(){val f=ByteArray(100){120.toByte()};assertEquals(0.0,FrameChangeAnalyzer.changedPixelPercent(f,f.copyOf(),28),0.0001)}
 @Test fun changedPixelsAreExpressedAsPercentage(){val a=ByteArray(100){100};val b=a.copyOf();for(i in 0 until 25)b[i]=180.toByte();assertEquals(25.0,FrameChangeAnalyzer.changedPixelPercent(a,b,28),0.0001)}
 @Test fun smallBrightnessNoiseIsIgnored(){assertEquals(0.0,FrameChangeAnalyzer.changedPixelPercent(byteArrayOf(100,100,100),byteArrayOf(110,115,127),28),0.0001)}
 @Test fun pixelStrideAndRowStrideAreRespected(){val r=FrameChangeAnalyzer.sampleLuma(byteArrayOf(10,0,20,0,30,0,40,0),2,2,4,2,2,2);assertEquals(listOf(10,20,30,40),r.map{it.toInt() and 255})}
 @Test fun adjacentPixelsFormOneRegion(){val a=ByteArray(25){100};val b=a.copyOf();for(y in 1..2)for(x in 2..3)b[y*5+x]=180.toByte();val r=FrameChangeAnalyzer.detectChangedRegions(a,b,5,5,28,2).single();assertEquals(4,r.pixelCount);assertEquals(2.5,r.centroidX,0.0001);assertEquals(1.5,r.centroidY,0.0001)}
 @Test fun disconnectedAreasStaySeparate(){val a=ByteArray(25){100};val b=a.copyOf();b[0]=180.toByte();b[24]=180.toByte();assertEquals(2,FrameChangeAnalyzer.detectChangedRegions(a,b,5,5,28,1).size)}
 @Test fun tinyRegionsAreFiltered(){val a=ByteArray(25){100};val b=a.copyOf();b[0]=180.toByte();b[12]=180.toByte();assertTrue(FrameChangeAnalyzer.detectChangedRegions(a,b,5,5,28,2).isEmpty())}
 @Test fun regionsAreSortedBySize(){val a=ByteArray(25){100};val b=a.copyOf();b[0]=180.toByte();b[6]=180.toByte();b[7]=180.toByte();b[11]=180.toByte();assertEquals(3,FrameChangeAnalyzer.detectChangedRegions(a,b,5,5,28,1).first().pixelCount)}
 @Test fun transientChangeDoesNotBecomeStable() {
  val reference=byteArrayOf(100)
  val persistence=ByteArray(1)
  val transient=FrameChangeAnalyzer.updatePersistenceMask(reference,byteArrayOf(180.toByte()),persistence,28,3)
  assertEquals(0,transient[0].toInt())
  val restored=FrameChangeAnalyzer.updatePersistenceMask(reference,byteArrayOf(100),persistence,28,3)
  assertEquals(0,restored[0].toInt())
 }
 @Test fun persistentChangeAppearsAfterRequiredFrames() {
  val reference=byteArrayOf(100)
  val persistence=ByteArray(1)
  repeat(2) {
   val mask=FrameChangeAnalyzer.updatePersistenceMask(reference,byteArrayOf(180.toByte()),persistence,28,3)
   assertEquals(0,mask[0].toInt())
  }
  val stable=FrameChangeAnalyzer.updatePersistenceMask(reference,byteArrayOf(180.toByte()),persistence,28,3)
  assertEquals(1,stable[0].toInt())
 }
 @Test fun persistenceResetsWhenPixelReturnsToReference() {
  val reference=byteArrayOf(100)
  val persistence=byteArrayOf(2)
  FrameChangeAnalyzer.updatePersistenceMask(reference,byteArrayOf(100),persistence,28,3)
  assertEquals(0,persistence[0].toInt())
 }

 @Test fun uniformExposureShiftDoesNotCreateChangedRegions() {
  val reference=ByteArray(100){100.toByte()}
  val brighter=ByteArray(100){140.toByte()}
  val mask=FrameChangeAnalyzer.updatePersistenceMask(reference,brighter,ByteArray(100),28,3)
  assertEquals(0,mask.count{it.toInt()!=0})
 }
 @Test fun localObjectChangeSurvivesGlobalExposureCompensation() {
  val reference=ByteArray(100){100.toByte()}
  val current=ByteArray(100){140.toByte()}
  current[55]=210.toByte()
  val persistence=ByteArray(100)
  repeat(3) { FrameChangeAnalyzer.updatePersistenceMask(reference,current,persistence,28,3) }
  val mask=FrameChangeAnalyzer.updatePersistenceMask(reference,current,persistence,28,3)
  assertEquals(1,mask.count{it.toInt()!=0})
 }

}
