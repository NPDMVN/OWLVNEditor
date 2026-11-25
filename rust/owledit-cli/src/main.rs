use std::env;
use std::fs;
use std::path::Path;
use std::time::Instant;

use owledit_core::crypto::OwlCodec;
use owledit_core::sanitizer::sanitize_playerprefs_xml;
use owledit_core::session::OwlSession;
use owledit_core::ssaid::{generate_random_ssaid, inspect_ssaid, update_ssaid_verified};

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 2 {
        print_usage();
        std::process::exit(1);
    }

    let res = match args[1].as_str() {
        "banner" => {
            println!("{}", owledit_core::core_banner());
            Ok(())
        }
        "summary" => cmd_summary(&args[2..]),
        "inspect" => cmd_inspect(&args[2..]),
        "preset" => cmd_preset(&args[2..]),
        "sanitize" => cmd_sanitize(&args[2..]),
        "ssaid" => cmd_ssaid(&args[2..]),
        "roundtrip-test" => cmd_roundtrip_test(&args[2..]),
        other => Err(format!("Unknown subcommand: {other}")),
    };

    if let Err(e) = res {
        eprintln!("ERROR: {e}");
        std::process::exit(2);
    }
}

fn print_usage() {
    eprintln!("owledit-cli — Otherworld Legends Save Engine CLI");
    eprintln!("Usage:");
    eprintln!("  owledit-cli banner");
    eprintln!("  owledit-cli summary <playerprefs.xml>");
    eprintln!("  owledit-cli inspect <playerprefs.xml> <key_or_md5>");
    eprintln!("  owledit-cli preset <in.xml> <out.xml> <preset_id>");
    eprintln!("  owledit-cli sanitize <in.xml> <out.xml>");
    eprintln!("  owledit-cli ssaid <settings_ssaid.xml> [package]");
    eprintln!("  owledit-cli roundtrip-test <playerprefs.xml>");
}

fn cmd_summary(args: &[String]) -> Result<(), String> {
    let path = args.first().ok_or("Missing <playerprefs.xml>")?;
    let t0 = Instant::now();
    let bytes = fs::read(path).map_err(|e| e.to_string())?;
    let session = OwlSession::open_xml_bytes(&bytes, OwlCodec::default(), None)
        .map_err(|e| e.to_string())?;
    let dash = session.build_dashboard().map_err(|e| e.to_string())?;
    let elapsed = t0.elapsed();

    println!(
        "Loaded {} entries ({} encrypted, {} resolved keys, {} NRBF, {} JSON) in {:.2} ms",
        dash.total_entries,
        dash.encrypted_entries,
        dash.resolved_keys,
        dash.nrbf_containers,
        dash.json_entries,
        elapsed.as_secs_f64() * 1000.0
    );
    println!(
        "Account: UID={:?} CloudID={:?} Email={:?}",
        dash.account.uid, dash.account.cloud_save_id, dash.account.email
    );
    println!("\nCurrencies & Tickets (EncryptValue XOR decoded):");
    for c in &dash.currencies {
        println!(
            "  {:32} = {:>12} (key={}, safeMax={})",
            c.key, c.value, c.encrypt_key, c.safe_max
        );
    }
    println!("\nAsura Power Investments:");
    for a in &dash.asura_stats {
        println!("  [{}] {:14} = {}", a.stat_id, a.code, a.value);
    }
    let unlocked_heroes = dash.heroes.iter().filter(|h| h.unlocked).count();
    let unlocked_menu = dash.menu_skins.iter().filter(|m| m.unlocked).count();
    println!(
        "\nHeroes: {}/{} unlocked | Menu Skins: {}/{} unlocked | Editable NRBF containers: {}/{}",
        unlocked_heroes,
        dash.heroes.len(),
        unlocked_menu,
        dash.menu_skins.len(),
        dash.containers.iter().filter(|c| c.editable).count(),
        dash.containers.len()
    );
    println!("\nTop 15 NRBF containers:");
    for c in dash.containers.iter().take(15) {
        println!(
            "  {:34} | {:>4} entries | editable={} | {}",
            c.plain_key, c.entry_count, c.editable, c.kind_label
        );
    }
    Ok(())
}

fn cmd_inspect(args: &[String]) -> Result<(), String> {
    if args.len() < 2 {
        return Err("Usage: inspect <playerprefs.xml> <key_or_md5>".into());
    }
    let bytes = fs::read(&args[0]).map_err(|e| e.to_string())?;
    let session = OwlSession::open_xml_bytes(&bytes, OwlCodec::default(), None)
        .map_err(|e| e.to_string())?;
    let info = session.inspect_entry(&args[1]).map_err(|e| e.to_string())?;
    println!(
        "{}",
        serde_json::to_string_pretty(&info).map_err(|e| e.to_string())?
    );
    Ok(())
}

fn cmd_preset(args: &[String]) -> Result<(), String> {
    if args.len() < 3 {
        return Err("Usage: preset <in.xml> <out.xml> <preset_id>".into());
    }
    let bytes = fs::read(&args[0]).map_err(|e| e.to_string())?;
    let mut session = OwlSession::open_xml_bytes(&bytes, OwlCodec::default(), None)
        .map_err(|e| e.to_string())?;
    let msg = session
        .apply_preset(&args[2], None, None)
        .map_err(|e| e.to_string())?;
    let diffs = session.compute_diff();
    let commit = session.commit_xml_verified().map_err(|e| e.to_string())?;
    fs::write(&args[1], &commit.xml_bytes).map_err(|e| e.to_string())?;
    println!(
        "{msg} | {} entries modified | SHA-256={}",
        diffs.len(),
        commit.sha256
    );
    Ok(())
}

fn cmd_sanitize(args: &[String]) -> Result<(), String> {
    if args.len() < 2 {
        return Err("Usage: sanitize <in.xml> <out.xml>".into());
    }
    let bytes = fs::read(&args[0]).map_err(|e| e.to_string())?;
    let res = sanitize_playerprefs_xml(&bytes, &[], true).map_err(|e| e.to_string())?;
    fs::write(&args[1], res.xml_text.as_bytes()).map_err(|e| e.to_string())?;
    println!(
        "Sanitized {} keys ({:?}), remaining={}",
        res.removed_keys.len(),
        res.removed_keys,
        res.remaining_entries
    );
    Ok(())
}

fn cmd_ssaid(args: &[String]) -> Result<(), String> {
    let path = args.first().ok_or("Missing <settings_ssaid.xml>")?;
    let bytes = fs::read(path).map_err(|e| e.to_string())?;
    let filter = args.get(1).map(|s| s.as_str());
    let insp = inspect_ssaid(&bytes, filter).map_err(|e| e.to_string())?;
    println!(
        "Format={:?} Total={} Matched={}",
        insp.format,
        insp.total_entries,
        insp.entries.len()
    );
    for e in &insp.entries {
        println!("  {} => {}", e.package, e.value);
    }
    if let Some(pkg) = filter {
        let rand_id = generate_random_ssaid();
        let upd = update_ssaid_verified(&bytes, pkg, &rand_id).map_err(|e| e.to_string())?;
        println!(
            "Dry-run SSAID update OK: {} -> {} ({} bytes)",
            upd.old_value,
            upd.new_value,
            upd.updated_bytes.len()
        );
    }
    Ok(())
}

fn cmd_roundtrip_test(args: &[String]) -> Result<(), String> {
    let path = Path::new(args.first().ok_or("Missing <playerprefs.xml>")?);
    let bytes = fs::read(path).map_err(|e| e.to_string())?;

    let t0 = Instant::now();
    let mut session = OwlSession::open_xml_bytes(&bytes, OwlCodec::default(), None)
        .map_err(|e| e.to_string())?;
    let open_ms = t0.elapsed().as_secs_f64() * 1000.0;

    // 1. Test round-trip of ALL editable NRBF containers!
    let dash = session.build_dashboard().map_err(|e| e.to_string())?;
    let mut nrbf_ok = 0usize;
    for c in dash.containers.iter().filter(|c| c.editable) {
        let parsed = session
            .read_nrbf_dict(&c.md5_key)
            .map_err(|e| format!("Read NRBF {} failed: {e}", c.plain_key))?;
        let re_bytes = parsed
            .serialize_bytes()
            .map_err(|e| format!("Serialize NRBF {} failed: {e}", c.plain_key))?;
        let reparsed = owledit_core::nrbf::NrbfDict::parse_bytes(&re_bytes)
            .map_err(|e| format!("Reparse NRBF {} failed: {e}", c.plain_key))?;
        if parsed.entries.len() != reparsed.entries.len() {
            return Err(format!(
                "NRBF entry count mismatch on {}: {} vs {}",
                c.plain_key,
                parsed.entries.len(),
                reparsed.entries.len()
            ));
        }
        for (a, b) in parsed.entries.iter().zip(reparsed.entries.iter()) {
            if a.key != b.key || a.value != b.value {
                return Err(format!(
                    "NRBF entry mismatch on {}: ({},{}) vs ({},{})",
                    c.plain_key, a.key, a.value, b.key, b.value
                ));
            }
        }
        nrbf_ok += 1;
    }

    // 2. Test full_safe_bundle preset + verified XML commit!
    let t1 = Instant::now();
    let preset_msg = session
        .apply_preset("full_safe_bundle", None, None)
        .map_err(|e| e.to_string())?;
    let diffs = session.compute_diff();
    let commit = session.commit_xml_verified().map_err(|e| e.to_string())?;
    let commit_ms = t1.elapsed().as_secs_f64() * 1000.0;

    // 3. Re-open committed XML and verify values!
    let reopened = OwlSession::open_xml_bytes(&commit.xml_bytes, OwlCodec::default(), None)
        .map_err(|e| e.to_string())?;
    let (soul, _, _) = reopened.get_encrypt_int_pair("SoulStoneCount");
    let (blue, _, _) = reopened.get_encrypt_int_pair("BlueStoneCount");
    let (red, _, _) = reopened.get_encrypt_int_pair("RedStoneCount");
    if soul != 5_000_000 || blue != 500_000 || red != 500_000 {
        return Err(format!(
            "Reopened currency mismatch: soul={soul} blue={blue} red={red}"
        ));
    }

    println!("=== ROUND-TRIP TEST PASSED ===");
    println!("Open + decrypt 3,576 entries: {open_ms:.2} ms");
    println!("NRBF containers round-tripped: {nrbf_ok}/{}", dash.containers.len());
    println!("Preset: {preset_msg}");
    println!("Diff entries: {} | Commit + self-verify: {commit_ms:.2} ms", diffs.len());
    println!("Verified currencies after re-open: Soul={soul}, Blue={blue}, Red={red}");
    println!("Final XML size: {} bytes | SHA-256: {}", commit.xml_bytes.len(), commit.sha256);
    Ok(())
}
