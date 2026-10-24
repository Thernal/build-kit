source "https://rubygems.org"

# The store lanes (fastlane/Fastfile). Ruby comes from .ruby-version; bundler 2.6.6, as the release pipeline runs it.
gem "fastlane", "~> 2.241"

# Plugins an app or another kit adds (firebase-kit: fastlane-plugin-firebase_app_distribution).
plugins_path = File.join(__dir__, "fastlane", "Pluginfile")
eval_gemfile(plugins_path) if File.exist?(plugins_path)
