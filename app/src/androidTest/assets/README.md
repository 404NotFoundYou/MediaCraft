# Generated media fixtures

These three small videos were generated for MediaCraft with FFmpeg's `testsrc2` video source and a generated 660 Hz sine tone. They contain no personal media and were not downloaded from a third party.

- `clip_source.mp4`: 320×240, 24 fps, H.264 / BT.709 / AAC stereo, four seconds.
- `clip_scaled.mp4`: same full clip, scaled to 160×120 and re-encoded at a lower bitrate, with the original audio track.
- `clip_trimmed.mp4`: the first three seconds of the source. It must not be treated as a duplicate of the complete clip.

The integration tests insert only their own uniquely prefixed media and delete exactly those created URIs during teardown. The production app does not load these assets.

The H.264 fixtures explicitly encode BT.709 VUI with `-x264-params colorprim=bt709:transfer=bt709:colormatrix=bt709`; setting only the container options did not reliably tag the generated streams. Untagged color information must remain unknown in the app rather than being assumed SDR.
