require "fileutils"
require "shellwords"

# Pure-Ruby side of the store lanes (fastlane/Fastfile): the project's release configuration and the checks,
# without fastlane, so they are unit-tested (fastlane/test, `make test-fastlane`).
#
# Everything project-shaped is data the build already reads — gradle.properties (app.flavors*, app.release.*),
# the app's Android build file and ios/project.yml — so a kit update changes no project's choices.
module ReleaseHelpers
  PLAY_NOTES_LIMIT = 500
  MAX_BUILD_NUMBER = 2_100_000_000 # Google Play's ceiling for a version code
  VERSION_NAME = /\A\d+\.\d+\.\d+\z/
  NO_ID_SUFFIX = "none"

  # app.release.<key> defaults; each key can be set for every app, or for one as app.release.<app>.<key>.
  RELEASE_DEFAULTS = {
    "closedTrack" => "alpha",              # Play track of closed testing
    "rollout" => "0.01",                   # Play production's first staged rollout fraction
    "internalGroup" => "Internal Testers", # TestFlight group of the beta upload
    "notesLocale" => "en-US"               # language of the release notes in both stores
  }.freeze

  Release = Struct.new(
    :app, :flavors, :production, :dev_apk, :beta, :id_suffixes,
    :android_id, :ios_bundle_id, :settings,
    keyword_init: true
  ) do
    def setting(key)
      settings.fetch(key)
    end

    # A flavor's application id suffix without the dot; nil for production's id.
    def id_suffix(flavor)
      id_suffixes.fetch(flavor)
    end

    def production_identity?(flavor)
      id_suffix(flavor).nil?
    end

    def android_package(flavor)
      [android_id, id_suffix(flavor)].compact.join(".")
    end

    def ios_bundle(flavor)
      raise ArgumentError, "no iOS bundle id for #{app}: set app.release.#{app}.iosBundleId" unless ios_bundle_id
      [ios_bundle_id, id_suffix(flavor)].compact.join(".")
    end

    # The flavor a channel builds; fails naming the key when the project has none for it.
    def beta!
      beta || raise(ArgumentError, "#{app} has no beta flavor: set app.release.beta (or app.release.#{app}.beta)")
    end

    # The flavors whose store applications a release number must stay above.
    def release_flavors
      [beta, production].compact.uniq
    end
  end

  module_function

  # gradle.properties as Gradle reads it for these keys: `key=value` lines, the last one winning.
  def read_properties(path)
    File.readlines(path, encoding: "UTF-8").each_with_object({}) do |line, properties|
      line = line.strip
      next if line.empty? || line.start_with?("#", "!")
      key, value = line.split("=", 2)
      properties[key.strip] = value.to_s.strip if value
    end
  end

  # The apps a repository ships: apps/<name>/ with an Android side, as make and the scripts find them.
  def list_apps(apps_dir)
    Dir.glob(File.join(apps_dir, "*", "android", "build.gradle.kts")).map { |file| File.basename(File.dirname(file, 2)) }.sort
  end

  # The app a lane works on: APP, or the only one.
  def choose_app(apps, requested)
    requested = requested.to_s.strip
    unless requested.empty?
      return requested if apps.include?(requested)
      raise ArgumentError, "APP=#{requested} is not an app here; apps: #{apps.join(', ')}"
    end
    return apps.first if apps.size == 1
    raise ArgumentError, "Set APP to one of: #{apps.join(', ')}" unless apps.empty?
    raise ArgumentError, "No apps: expected apps/<name>/android/build.gradle.kts"
  end

  def release_config(properties:, app:, android_build_file:, ios_project_file:)
    flavors = list(properties["app.flavors"])
    raise ArgumentError, "app.flavors lists no flavor" if flavors.empty?
    production = present(properties["app.flavors.production"]) || flavors.last
    setting = ->(key) { present(properties["app.release.#{app}.#{key}"]) || present(properties["app.release.#{key}"]) }

    release_production = flavor!(setting.("production") || production, flavors, "app.release.production")
    dev_apk = flavor!(setting.("devApk") || default_dev_flavor(properties, flavors), flavors, "app.release.devApk")
    beta = setting.("beta") || (flavors.include?("beta") ? "beta" : nil)
    flavor!(beta, flavors, "app.release.beta") if beta

    settings = RELEASE_DEFAULTS.to_h { |key, default| [key, setting.(key) || default] }
    rollout!(settings.fetch("rollout"))

    Release.new(
      app: app,
      flavors: flavors,
      production: release_production,
      dev_apk: dev_apk,
      beta: beta,
      id_suffixes: flavors.to_h { |flavor| [flavor, id_suffix(properties, flavor, production)] },
      android_id: android_id(properties, app, android_build_file),
      ios_bundle_id: setting.("iosBundleId") || ios_bundle_id(ios_project_file),
      settings: settings
    )
  end

  # The build's rule (build-logic/…/flavor/AppFlavors.kt): none for production and idSuffix=none, the override
  # when set, else the flavor's name.
  def id_suffix(properties, flavor, production)
    return nil if flavor == production
    override = present(properties["app.flavors.#{flavor}.idSuffix"])&.delete_prefix(".")
    return flavor if override.nil? || override.empty?
    override == NO_ID_SUFFIX ? nil : override
  end

  # The Android module's own `applicationId = "…"`, else the convention's default: the namespace and the module
  # path below the modules root, `-` read as `.` (build-logic/…/ProjectExtensions.kt, namespaceFor).
  def android_id(properties, app, build_file)
    text = File.exist?(build_file) ? File.read(build_file, encoding: "UTF-8") : ""
    declared = text[/^\s*applicationId\s*=\s*"([^"]+)"/, 1]
    return declared if declared
    segments = ["apps", *app.split("-"), "android"].map { |segment| segment.gsub(/[^A-Za-z0-9_]/, "") }
    [properties.fetch("app.namespace"), *segments.reject(&:empty?)].join(".")
  end

  # ios/project.yml's `PRODUCT_BUNDLE_IDENTIFIER: <id>$(BUNDLE_ID_SUFFIX)`, nil without one.
  def ios_bundle_id(project_file)
    return nil unless File.exist?(project_file)
    File.read(project_file, encoding: "UTF-8")[/PRODUCT_BUNDLE_IDENTIFIER:\s*"?([A-Za-z0-9.-]+)\$\(BUNDLE_ID_SUFFIX\)/, 1]
  end

  def version_name!(value)
    value = value.to_s.strip
    raise ArgumentError, "VERSION_NAME must be X.Y.Z, got #{value.inspect}" unless value.match?(VERSION_NAME)
    value
  end

  def build_number!(value)
    raw = value.to_s.strip
    number = raw.match?(/\A\d+\z/) ? raw.to_i : 0
    return number if number.between?(1, MAX_BUILD_NUMBER)
    raise ArgumentError, "BUILD_NUMBER must be a whole number from 1 to #{MAX_BUILD_NUMBER}, got #{raw.inspect}"
  end

  # The override file wins when it exists and isn't blank; otherwise the saved file.
  def release_notes(saved_path:, override_path: nil)
    if override_path && File.exist?(override_path)
      text = File.read(override_path, encoding: "UTF-8")
      return text.rstrip unless text.strip.empty?
    end
    File.read(saved_path, encoding: "UTF-8").rstrip
  end

  def check_notes!(text)
    raise ArgumentError, "release notes are empty" if text.strip.empty?
    if text.length > PLAY_NOTES_LIMIT
      raise ArgumentError, "release notes are #{text.length} characters; Google Play allows #{PLAY_NOTES_LIMIT}"
    end
    nil
  end

  def check_build_number!(input:, highest:)
    return nil if input > highest
    raise ArgumentError, "build number #{input} must be above #{highest}, the highest in Google Play and App Store Connect"
  end

  # Only a build on the track the previous step put it on moves on: it is this channel's flavor and was uploaded.
  def check_promotable!(build_number:, codes_on_track:, track:, lane:)
    return nil if codes_on_track.include?(build_number)
    raise ArgumentError, "build #{build_number} is not on Play's #{track} track; run #{lane} for it first"
  end

  def check_groups!(wanted:, existing:)
    missing = wanted - existing
    return nil if missing.empty?
    raise ArgumentError,
          "TestFlight group(s) not found in App Store Connect: #{missing.join(', ')} " \
          "(existing: #{existing.empty? ? 'none' : existing.join(', ')})"
  end

  # The values are checked (version_name!, build_number!), so they need no quoting.
  def gradle_args(version_name:, build_number:)
    "-Papp.versionName=#{version_name} -Papp.versionCode=#{build_number}"
  end

  def version_xcargs(version_name:, build_number:)
    "MARKETING_VERSION=#{version_name} CURRENT_PROJECT_VERSION=#{build_number}"
  end

  # Writes the text byte for byte; returns the file path.
  def write_play_changelog(metadata_dir:, locale:, version_code:, text:)
    dir = File.join(metadata_dir, locale, "changelogs")
    FileUtils.mkdir_p(dir)
    path = File.join(dir, "#{version_code}.txt")
    File.binwrite(path, text)
    path
  end

  # upload_to_play_store options that move a build already in the Play library to a track.
  # version_codes_to_retain (not version_code) is what makes supply's update_track create the release.
  def promote_options(package_name:, json_key:, track:, build_number:, release_status:, rollout: nil)
    options = {
      package_name: package_name,
      json_key: json_key,
      track: track,
      version_codes_to_retain: [build_number],
      skip_upload_aab: true,
      skip_upload_apk: true,
      release_status: release_status,
      skip_upload_metadata: true,
      skip_upload_images: true,
      skip_upload_screenshots: true
    }
    options[:rollout] = rollout if rollout
    options
  end

  # The keystore file name keystore.properties names (storeFile), which the build resolves next to it.
  def keystore_file_name!(properties_text)
    name = properties_text[/^\s*storeFile\s*=\s*(.+?)\s*$/, 1].to_s
    unless name.match?(/\A[A-Za-z0-9._-]+\z/) && !name.start_with?(".")
      raise ArgumentError, "keystore.properties must name storeFile as a plain file name, got #{name.inspect}"
    end
    name
  end

  # git's ssh for match: only the given key and known hosts, nothing from ~/.ssh.
  def match_ssh_command(key_file:, known_hosts_file:)
    "ssh -F /dev/null -i #{Shellwords.escape(key_file)} -o IdentitiesOnly=yes " \
      "-o UserKnownHostsFile=#{Shellwords.escape(known_hosts_file)} -o StrictHostKeyChecking=yes"
  end

  def list(value)
    value.to_s.split(",").map(&:strip).reject(&:empty?)
  end

  def present(value)
    value = value.to_s.strip
    value.empty? ? nil : value
  end

  def flavor!(flavor, flavors, key)
    return flavor if flavors.include?(flavor)
    raise ArgumentError, "#{key}=#{flavor} is not in app.flavors (#{flavors.join(', ')})"
  end

  def default_dev_flavor(properties, flavors)
    flavors.include?("dev") ? "dev" : present(properties["app.flavors.default"]) || flavors.first
  end

  def rollout!(value)
    fraction = Float(value, exception: false)
    return nil if fraction && fraction.positive? && fraction <= 1
    raise ArgumentError, "app.release.rollout must be a fraction above 0 and at most 1, got #{value.inspect}"
  end
end
