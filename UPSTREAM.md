# Vendored upstream versions

Snapshot taken 2026-10-02 from the default branches, shallow clones (no history):

| Component | Repository | Commit |
|---|---|---|
| App | https://github.com/yuliskov/SmartTube | 21cc4392e87e223885d48c5796c7c4834b5cabb3 |
| MediaServiceCore | https://github.com/yuliskov/MediaServiceCore | 0dd197bd4df8a7453f590c377a9c699d33145d6e |
| SharedModules | https://github.com/yuliskov/SharedModules | 13f5687dd6757b02fbcdf14c5403d0339e377db5 |

`exoplayer-amzn-2.10.6/`, `leanback-1.0.0/` and `fragment-1.1.0/` were already plain folders in the app repo.
Removed from the copy to keep it small: git metadata, `images/`, `fastlane/`, upstream `.github/`, and
`MediaServiceCore/youtubeapi/src/test/resources` (109 MB of saved web pages used only by unit tests).
