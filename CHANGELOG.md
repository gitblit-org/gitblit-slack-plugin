## Gitblit Slack plugin

### 1.2.0

- Allow specifying an emoji for git events and an emoji for ticket events
- Partial transformation from Markdown->Slack markup for ticket descriptions and comments
- Improve ticket branch push logging by listing up to 5 commits and linking to the compare pages.
- Switch to a cached thread pool

### 1.1.0

- Improve push logging by listing up to 5 commits per push and linking to the log & compare pages.  This now feels more like the Gitblit reflog.
- Added setting to disable ticket comment logging
- Fixed project-channel sharding to work as described

### 1.0.1

- Stop threadpool when the plugin is stopped

### 1.0.0

- Initial release

