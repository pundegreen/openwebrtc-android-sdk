/*
 * Copyright (c) 2015, Ericsson AB.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */
package com.ericsson.research.owr.sdk;

import android.test.MoreAsserts;
import android.util.Log;

import com.ericsson.research.owr.AudioRenderer;
import com.ericsson.research.owr.MediaSource;
import com.ericsson.research.owr.MediaType;
import com.ericsson.research.owr.SourceType;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MediaSourceTests extends OwrTestCase {
    private static final String TAG = "MediaSourceTests";

    private int mListenerCallCount = 0;

    public class MockListener implements MediaSourceListener {
        private final CountDownLatch mLatch;

        public MockListener(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void setMediaSource(final MediaSource mediaSource) {
            mListenerCallCount += 1;
            if (mLatch != null) {
                mLatch.countDown();
            }
        }
    }

    public void testMediaSourceListenerSet() {
        final MediaSourceListenerSet set = new MediaSourceListenerSet();
        TestUtils.synchronous().run(new TestUtils.SynchronousBlock() {
            @Override
            public void run(final CountDownLatch latch) {
                set.addListener(new MockListener(latch));
            }
        });
        set.notifyListeners(null);
        assertEquals(1, mListenerCallCount);

        long freeMemBeforeGc = Runtime.getRuntime().freeMemory();
        System.gc();
        TestUtils.sleep(500);
        if (Runtime.getRuntime().freeMemory() < freeMemBeforeGc) {
            set.notifyListeners(null); // This should not call the listener, as it was GC'd
            assertEquals(1, mListenerCallCount);
            TestUtils.synchronous().run(new TestUtils.SynchronousBlock() {
                @Override
                public void run(final CountDownLatch latch) {
                    set.addListener(new MockListener(latch));
                }
            });
            // The previous assertion would fail here, as we didn't handle that it was asynchronous
            assertEquals(2, mListenerCallCount); // should be signaled when it's added
        } else {
            Log.w(TAG, "Skipping GC test, no memory was free'd");
        }

        try {
            set.addListener(null);
            throw new RuntimeException("should not be reached");
        } catch (NullPointerException ignored) {}
    }

    public void testMicrophoneSource() {
        final MicrophoneSource source = MicrophoneSource.getInstance();
        assertEquals("Default audio input", source.getName());
        final MediaSource[] audioSource = new MediaSource[1];
        TestUtils.synchronous().run(new TestUtils.SynchronousBlock() {
            @Override
            public void run(final CountDownLatch latch) {
                source.addMediaSourceListener(new MediaSourceListener() {
                    @Override
                    public void setMediaSource(final MediaSource mediaSource) {
                        assertNotNull(mediaSource);
                        audioSource[0] = mediaSource;
                        MoreAsserts.assertContentsInOrder(mediaSource.getMediaType(), MediaType.AUDIO);
                        assertEquals(source.getName(), mediaSource.getName());
                        assertSame(SourceType.CAPTURE, mediaSource.getType());
                        latch.countDown();
                    }
                });
            }
        }).run(new TestUtils.SynchronousBlock() {
            @Override
            public void run(final CountDownLatch latch) {
                source.addMediaSourceListener(new MediaSourceListener() {
                    @Override
                    public void setMediaSource(final MediaSource mediaSource) {
                        assertNotNull(mediaSource);
                        assertSame(audioSource[0], mediaSource);
                        latch.countDown();
                    }
                });
            }
        });
    }

    public void testCameraSourceBasicFunctionality() {
        final CameraSource source = CameraSource.getInstance();

        assertEquals(2, source.getCount()); // Test needs to be run on a device with two cameras
        assertEquals("Front facing Camera", source.getName(0));
        assertEquals("Back facing Camera", source.getName(1));
        List<String> emptyPipelineDump = source.dumpPipelineGraphs();
        assertEquals(2, emptyPipelineDump.size());
        assertNotNull(emptyPipelineDump.get(0));
        assertNotNull(emptyPipelineDump.get(1));

        TestUtils.synchronous().run(new TestUtils.SynchronousBlock() {
            @Override
            public void run(final CountDownLatch latch) {
                source.addMediaSourceListener(new MediaSourceListener() {
                    private boolean once = true;
                    @Override
                    public void setMediaSource(final MediaSource mediaSource) {
                        if (once) {
                            assertNotNull(mediaSource);
                            assertEquals("Front facing Camera", mediaSource.getName());
                            latch.countDown();
                            once = false;
                        }
                    }
                });
            }
        }).run(new TestUtils.SynchronousBlock() {
            @Override
            public void run(final CountDownLatch latch) {
                source.addMediaSourceListener(new MediaSourceListener() {
                    @Override
                    public void setMediaSource(final MediaSource mediaSource) {
                        if (mediaSource != null && "Back facing Camera".equals(mediaSource.getName())) {
                            latch.countDown();
                        }
                    }
                });
                source.selectSource(1);
            }
        });
    }
}