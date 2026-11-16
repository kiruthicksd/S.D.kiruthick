const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-smartaim-a23d95b4-59f9-41e5-a875-66cef122ffb7";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

function dbFor(uid) {
  const ctx = uid ? testEnv.authenticatedContext(uid) : testEnv.unauthenticatedContext();
  return ctx.firestore(DATABASE_ID);
}

test("Unauthenticated user cannot read or write profiles, photos, locations, notifications, or AI messages", async () => {
  const unauth = dbFor(null);
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("photos").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("location_history").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("ai_messages").get());
  await assertFails(unauth.collection("shared_photos").where("isPublic", "==", true).get());
});

test("Owner can create and update their profile, while cross-user access and shadow fields fail", async () => {
  const alice = dbFor(ALICE_UID);
  const bob = dbFor(BOB_UID);
  const now = tsNow();

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost / shadow field rejected
  await assertFails(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read or overwrite Alice's PII profile
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: tsNow(),
    })
  );
});

test("Owner can create, update caption, and delete photos; cross-user blocked", async () => {
  const alice = dbFor(ALICE_UID);
  const bob = dbFor(BOB_UID);
  const now = tsNow();

  const photoRef = alice.collection("users").doc(ALICE_UID).collection("photos").doc("photo_1");
  await assertSucceeds(
    photoRef.set({
      userId: ALICE_UID,
      caption: "ESP32 Test",
      description: "Night capture",
      imageBase64: "ZmFrZQ==",
      source: "esp32",
      hasLocation: true,
      latitude: 37.7749,
      longitude: -122.4194,
      isPublic: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    photoRef.update({
      caption: "Updated Caption",
      updatedAt: tsNow(),
    })
  );

  // Bob cannot read Alice's private photos
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("photos").get());
  await assertFails(bob.collection("users").doc(ALICE_UID).collection("photos").doc("photo_1").get());
});

test("Shared photos require explicit isPublic=true and aligned query", async () => {
  const alice = dbFor(ALICE_UID);
  const bob = dbFor(BOB_UID);
  const now = tsNow();

  await assertSucceeds(
    alice.collection("shared_photos").doc("pub_1").set({
      userId: ALICE_UID,
      authorName: "Alice",
      caption: "Public Skyline",
      description: "Shared view",
      imageBase64: "ZmFrZQ==",
      source: "camera",
      hasLocation: false,
      isPublic: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Signed-in Bob can query with isPublic == true, but unfiltered fails
  await assertSucceeds(bob.collection("shared_photos").where("isPublic", "==", true).get());
  await assertFails(bob.collection("shared_photos").get());

  // Bob cannot delete Alice's shared photo
  await assertFails(bob.collection("shared_photos").doc("pub_1").delete());
});

test("Location history, notifications, and AI messages enforce ownership and schema", async () => {
  const alice = dbFor(ALICE_UID);
  const bob = dbFor(BOB_UID);
  const now = tsNow();

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("location_history").doc("loc_1").set({
      userId: ALICE_UID,
      label: "Base Station",
      latitude: 12.9716,
      longitude: 77.5946,
      accuracyMeters: 8.5,
      createdAt: now,
    })
  );

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").set({
      userId: ALICE_UID,
      title: "Photo Uploaded",
      message: "Saved to gallery",
      category: "photo",
      isRead: false,
      createdAt: now,
    })
  );

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").update({
      isRead: true,
    })
  );

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("ai_messages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "user",
      text: "Describe this location",
      createdAt: now,
    })
  );

  await assertFails(bob.collection("users").doc(ALICE_UID).collection("ai_messages").get());
});
