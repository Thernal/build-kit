require "minitest/autorun"
require "tmpdir"
require "fileutils"
require "release_helpers"

class ReleaseHelpersTest < Minitest::Test
  PROPERTIES = {
    "app.namespace" => "com.example",
    "app.flavors" => "regress, dev, beta, prod",
    "app.flavors.production" => "prod",
    "app.flavors.default" => "dev",
    "app.flavors.beta.idSuffix" => "none",
    "app.modules.root" => ""
  }.freeze

  def config(properties = PROPERTIES, app: "customer", android: nil, ios: nil)
    Dir.mktmpdir do |dir|
      android_file = File.join(dir, "build.gradle.kts")
      ios_file = File.join(dir, "project.yml")
      File.write(android_file, android) if android
      File.write(ios_file, ios) if ios
      ReleaseHelpers.release_config(properties: properties, app: app, android_build_file: android_file,
                                    ios_project_file: ios_file)
    end
  end

  # ─── Configuration ─────────────────────────────────────────────────────────

  def test_reads_gradle_properties_last_value_winning
    Dir.mktmpdir do |dir|
      path = File.join(dir, "gradle.properties")
      File.write(path, "# comment\napp.flavors = dev,prod\n\napp.flavors=dev,beta,prod\nother=a=b\n")
      properties = ReleaseHelpers.read_properties(path)
      assert_equal "dev,beta,prod", properties["app.flavors"]
      assert_equal "a=b", properties["other"]
    end
  end

  def test_lists_apps_with_an_android_side
    Dir.mktmpdir do |dir|
      %w[customer partner].each { |app| FileUtils.mkdir_p(File.join(dir, app, "android")) }
      %w[customer partner].each { |app| File.write(File.join(dir, app, "android", "build.gradle.kts"), "") }
      FileUtils.mkdir_p(File.join(dir, "docs"))
      assert_equal %w[customer partner], ReleaseHelpers.list_apps(dir)
    end
  end

  def test_chooses_the_requested_or_the_only_app
    assert_equal "customer", ReleaseHelpers.choose_app(%w[customer], nil)
    assert_equal "partner", ReleaseHelpers.choose_app(%w[customer partner], " partner ")
    e = assert_raises(ArgumentError) { ReleaseHelpers.choose_app(%w[customer partner], "") }
    assert_equal "Set APP to one of: customer, partner", e.message
    assert_raises(ArgumentError) { ReleaseHelpers.choose_app(%w[customer], "kiosk") }
    assert_raises(ArgumentError) { ReleaseHelpers.choose_app([], nil) }
  end

  def test_channels_default_to_dev_beta_and_production
    release = config
    assert_equal "dev", release.dev_apk
    assert_equal "beta", release.beta!
    assert_equal "prod", release.production
    assert_equal %w[beta prod], release.release_flavors
    assert_equal "alpha", release.setting("closedTrack")
    assert_equal "0.01", release.setting("rollout")
    assert_equal "Internal Testers", release.setting("internalGroup")
    assert_equal "en-US", release.setting("notesLocale")
  end

  def test_channels_and_settings_are_project_data_overridable_per_app
    properties = PROPERTIES.merge(
      "app.flavors" => "dev,staging,prod",
      "app.release.beta" => "staging",
      "app.release.client.beta" => "dev",
      "app.release.rollout" => "0.05",
      "app.release.client.closedTrack" => "qa"
    )
    assert_equal "staging", config(properties, app: "store").beta!
    client = config(properties, app: "client")
    assert_equal "dev", client.beta!
    assert_equal "qa", client.setting("closedTrack")
    assert_equal "0.05", client.setting("rollout")
  end

  def test_no_beta_flavor_fails_only_when_asked_for
    release = config(PROPERTIES.merge("app.flavors" => "dev,prod"))
    assert_nil release.beta
    assert_equal %w[prod], release.release_flavors
    e = assert_raises(ArgumentError) { release.beta! }
    assert_includes e.message, "app.release.beta"
  end

  def test_rejects_channels_outside_the_flavors_and_a_bad_rollout
    assert_raises(ArgumentError) { config(PROPERTIES.merge("app.release.beta" => "qa")) }
    assert_raises(ArgumentError) { config(PROPERTIES.merge("app.release.devApk" => "local")) }
    assert_raises(ArgumentError) { config(PROPERTIES.merge("app.release.rollout" => "0")) }
    assert_raises(ArgumentError) { config(PROPERTIES.merge("app.release.rollout" => "5%")) }
  end

  def test_ids_follow_the_build_suffix_rule
    release = config(PROPERTIES.merge("app.flavors.regress.idSuffix" => ".dev"),
                     android: "android {\n    defaultConfig {\n        applicationId = \"com.example.shop\"\n    }\n}\n",
                     ios: "        PRODUCT_BUNDLE_IDENTIFIER: com.example.shop.ios$(BUNDLE_ID_SUFFIX)\n")
    assert_equal "com.example.shop", release.android_package("prod")
    assert_equal "com.example.shop", release.android_package("beta")
    assert_equal "com.example.shop.dev", release.android_package("dev")
    assert_equal "com.example.shop.dev", release.android_package("regress")
    assert_equal "com.example.shop.ios", release.ios_bundle("beta")
    assert_equal "com.example.shop.ios.dev", release.ios_bundle("dev")
    assert release.production_identity?("beta")
    refute release.production_identity?("dev")
  end

  def test_default_android_id_and_ios_bundle_property
    properties = PROPERTIES.merge("app.modules.root" => "fixture", "app.release.home-shop.iosBundleId" => "com.example.c")
    release = config(properties, app: "home-shop")
    assert_equal "com.example.apps.home.shop.android", release.android_package("prod")
    assert_equal "com.example.c", release.ios_bundle("prod")
  end

  def test_ios_bundle_without_project_or_property_names_the_key
    e = assert_raises(ArgumentError) { config.ios_bundle("prod") }
    assert_includes e.message, "app.release.customer.iosBundleId"
  end

  # ─── Inputs ────────────────────────────────────────────────────────────────

  def test_version_name_and_build_number
    assert_equal "2.4.0", ReleaseHelpers.version_name!(" 2.4.0 ")
    assert_raises(ArgumentError) { ReleaseHelpers.version_name!("2.4") }
    assert_raises(ArgumentError) { ReleaseHelpers.version_name!("1.0\" ; rm -rf / #") }
    assert_equal 84, ReleaseHelpers.build_number!("84")
    assert_raises(ArgumentError) { ReleaseHelpers.build_number!("0") }
    assert_raises(ArgumentError) { ReleaseHelpers.build_number!("8_4") }
    assert_raises(ArgumentError) { ReleaseHelpers.build_number!("2100000001") }
  end

  def with_files(saved:, override: nil)
    Dir.mktmpdir do |d|
      s = File.join(d, "saved.txt")
      File.write(s, saved)
      o = nil
      unless override.nil?
        o = File.join(d, "override.txt")
        File.write(o, override)
      end
      yield s, o
    end
  end

  def test_override_notes_win_when_present
    with_files(saved: "saved\n", override: "override \n\n") do |s, o|
      assert_equal "override", ReleaseHelpers.release_notes(saved_path: s, override_path: o)
    end
  end

  def test_saved_notes_used_when_override_blank_or_missing
    with_files(saved: "saved  \n", override: " \n\n") do |s, o|
      assert_equal "saved", ReleaseHelpers.release_notes(saved_path: s, override_path: o)
      assert_equal "saved", ReleaseHelpers.release_notes(saved_path: s, override_path: "#{o}.missing")
    end
  end

  def test_notes_limits
    assert_equal "release notes are empty", assert_raises(ArgumentError) { ReleaseHelpers.check_notes!(" ") }.message
    assert_nil ReleaseHelpers.check_notes!("a" * 500)
    e = assert_raises(ArgumentError) { ReleaseHelpers.check_notes!("a" * 501) }
    assert_equal "release notes are 501 characters; Google Play allows 500", e.message
  end

  def test_play_changelog_keeps_the_text_byte_for_byte
    text = %q(a 'b' "c" $HOME `x` \\n ə)
    Dir.mktmpdir do |d|
      path = ReleaseHelpers.write_play_changelog(metadata_dir: d, locale: "en-US", version_code: 83, text: text)
      assert_equal File.join(d, "en-US", "changelogs", "83.txt"), path
      assert_equal text.b, File.binread(path)
    end
  end

  # ─── Checks and options ────────────────────────────────────────────────────

  def test_build_number_must_be_above_the_highest
    e = assert_raises(ArgumentError) { ReleaseHelpers.check_build_number!(input: 83, highest: 83) }
    assert_equal "build number 83 must be above 83, the highest in Google Play and App Store Connect", e.message
    assert_nil ReleaseHelpers.check_build_number!(input: 84, highest: 83)
  end

  def test_only_a_build_on_the_previous_track_is_promoted
    assert_nil ReleaseHelpers.check_promotable!(build_number: 84, codes_on_track: [83, 84], track: "internal",
                                                lane: "android beta_upload")
    e = assert_raises(ArgumentError) do
      ReleaseHelpers.check_promotable!(build_number: 84, codes_on_track: [83], track: "production", lane: "android prod_upload")
    end
    assert_equal "build 84 is not on Play's production track; run android prod_upload for it first", e.message
  end

  def test_testflight_groups_named_when_missing
    assert_nil ReleaseHelpers.check_groups!(wanted: ["A"], existing: %w[A B])
    e = assert_raises(ArgumentError) { ReleaseHelpers.check_groups!(wanted: %w[A C], existing: %w[A B]) }
    assert_includes e.message, "C"
    assert_includes e.message, "existing: A, B"
  end

  def test_version_arguments
    assert_equal "-Papp.versionName=2.4.1 -Papp.versionCode=83",
                 ReleaseHelpers.gradle_args(version_name: "2.4.1", build_number: 83)
    assert_equal "MARKETING_VERSION=2.4.1 CURRENT_PROJECT_VERSION=83",
                 ReleaseHelpers.version_xcargs(version_name: "2.4.1", build_number: 83)
  end

  def test_promote_options_retain_the_version_code
    o = ReleaseHelpers.promote_options(package_name: "p", json_key: "/k", track: "production",
                                       build_number: 83, release_status: "inProgress", rollout: "0.01")
    assert_equal [83], o[:version_codes_to_retain]
    refute o.key?(:version_code)
    assert_equal true, o[:skip_upload_aab]
    assert_equal true, o[:skip_upload_apk]
    assert_equal "0.01", o[:rollout]
    refute ReleaseHelpers.promote_options(package_name: "p", json_key: "/k", track: "alpha", build_number: 5,
                                          release_status: "completed").key?(:rollout)
  end

  def test_keystore_file_name_is_a_plain_name
    assert_equal "upload.jks", ReleaseHelpers.keystore_file_name!("storePassword=x\nstoreFile = upload.jks \n")
    assert_raises(ArgumentError) { ReleaseHelpers.keystore_file_name!("storeFile=../upload.jks\n") }
    assert_raises(ArgumentError) { ReleaseHelpers.keystore_file_name!("storeFile=/tmp/upload.jks\n") }
    assert_raises(ArgumentError) { ReleaseHelpers.keystore_file_name!("storePassword=x\n") }
  end

  def test_match_ssh_reads_nothing_from_the_machine
    command = ReleaseHelpers.match_ssh_command(key_file: "/tmp/a key", known_hosts_file: "/tmp/hosts")
    assert_includes command, "-F /dev/null"
    assert_includes command, "-i /tmp/a\\ key"
    assert_includes command, "StrictHostKeyChecking=yes"
  end
end
