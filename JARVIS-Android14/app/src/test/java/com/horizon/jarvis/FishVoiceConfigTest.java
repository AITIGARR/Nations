package com.horizon.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

public class FishVoiceConfigTest {
    @Test public void presetHasValidReferenceId() {
        assertTrue(FishVoice.isValidVoiceId(FishVoice.DEFAULT_VOICE_ID));
    }
    @Test public void rejectInvalidVoiceIds() {
        assertFalse(FishVoice.isValidVoiceId(null));
        assertFalse(FishVoice.isValidVoiceId(""));
        assertFalse(FishVoice.isValidVoiceId("iron-man"));
        assertFalse(FishVoice.isValidVoiceId("1234"));
    }
    @Test public void modelPageMatchesConfiguredPreset() {
        assertTrue(FishVoice.SOURCE_VOICE_URL.contains(FishVoice.DEFAULT_VOICE_ID));
    }
}
