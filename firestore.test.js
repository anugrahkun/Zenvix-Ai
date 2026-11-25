const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_zenvix_123";
const BOB_UID = "bob_zenvix_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write any Zenvix AI collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("artifacts").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set({
      userId: ALICE_UID,
      title: "Unauthorized",
      modelId: "gemini-3.8-flash",
      stylePreset: "BALANCED",
      thinkingEnabled: false,
      pinned: false,
      lastMessagePreview: "",
      messageCount: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid AiProject create, partial update, list, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");

  await assertSucceeds(
    projectRef.set({
      userId: ALICE_UID,
      name: "Kotlin Architect",
      description: "Clean Architecture & Compose helper",
      systemPrompt: "Respond with production Kotlin Compose code.",
      stylePreset: "CODE_ARCHITECT",
      accentIndex: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    projectRef.update({
      name: "Kotlin Compose Architect",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );

  // Reject ghost field & future timestamp
  await assertFails(
    projectRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    projectRef.update({
      name: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(projectRef.delete());
});

test("Authenticated owner: valid ChatThread & ChatMessage lifecycle succeeds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const chatRef = aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1");

  // Create without optional projectId (matches Kotlin .filterValues { it != null })
  await assertSucceeds(
    chatRef.set({
      userId: ALICE_UID,
      title: "Build a Weather Widget",
      modelId: "gemini-3.8-flash",
      stylePreset: "BALANCED",
      thinkingEnabled: true,
      pinned: false,
      lastMessagePreview: "Create an HTML weather card",
      messageCount: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Add user & model messages under chat_1
  const msg1Ref = chatRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msg1Ref.set({
      userId: ALICE_UID,
      chatId: "chat_1",
      role: "user",
      content: "Create an HTML weather card",
      createdAt: new Date(Date.now() - 8000),
    })
  );

  const msg2Ref = chatRef.collection("messages").doc("msg_2");
  await assertSucceeds(
    msg2Ref.set({
      userId: ALICE_UID,
      chatId: "chat_1",
      role: "model",
      content: "Here is a clean responsive weather card.",
      thinkingSummary: "1. Structure semantic HTML. 2. Style glass card.",
      artifactTitle: "Weather Card",
      artifactLanguage: "html",
      artifactContent: "<div>Sunny 28C</div>",
      modelId: "gemini-3.8-flash",
      createdAt: new Date(Date.now() - 6000),
    })
  );

  // Partial update on chat thread
  await assertSucceeds(
    chatRef.update({
      pinned: true,
      messageCount: 2,
      lastMessagePreview: "Here is a clean responsive weather card.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId 'id' leakage or createdAt tampering or message update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_bad").set({
      id: "chat_bad",
      userId: ALICE_UID,
      title: "Bad",
      modelId: "gemini-3.8-flash",
      stylePreset: "BALANCED",
      thinkingEnabled: false,
      pinned: false,
      lastMessagePreview: "",
      messageCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    chatRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(msg1Ref.update({ content: "Tampered" }));
});

test("Authenticated owner: valid SavedArtifact create, update, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 3000);

  const artifactRef = aliceDb.collection("users").doc(ALICE_UID).collection("artifacts").doc("art_1");
  await assertSucceeds(
    artifactRef.set({
      userId: ALICE_UID,
      title: "Landing Page",
      language: "html",
      content: "<h1>Zenvix AI</h1>",
      chatId: "chat_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    artifactRef.update({
      title: "Zenvix Landing Page",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read, list, or write Alice's collections
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("artifacts").doc("art_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("artifacts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("artifacts").doc("art_1").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: custom BYOK model IDs (opus-5.5, febal-5.1, sonnet-5.5) succeed and invalid model IDs fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  for (const modelId of ["opus-5.5", "febal-5.1", "sonnet-5.5", "anthropic/claude-opus-5.5"]) {
    const ref = aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc(`chat_${modelId.replace(/[^a-zA-Z0-9]/g, "_")}`);
    await assertSucceeds(
      ref.set({
        userId: ALICE_UID,
        title: `Test ${modelId}`,
        modelId,
        stylePreset: "BALANCED",
        thinkingEnabled: true,
        pinned: false,
        lastMessagePreview: "Hi",
        messageCount: 0,
        createdAt: now,
        updatedAt: now,
      })
    );
  }

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_bad_model").set({
      userId: ALICE_UID,
      title: "Invalid Model",
      modelId: "bad model with spaces!",
      stylePreset: "BALANCED",
      thinkingEnabled: false,
      pinned: false,
      lastMessagePreview: "",
      messageCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated owner: UserApp create, update, delete, and cross-user isolation succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const appRef = aliceDb.collection("users").doc(ALICE_UID).collection("userApps").doc("app_1");

  await assertSucceeds(
    appRef.set({
      userId: ALICE_UID,
      name: "Expense Tracker",
      description: "Personal daily budget tracker",
      htmlCode: "<!DOCTYPE html><html><body><h1>Tracker</h1></body></html>",
      lastPrompt: "Create an expense tracker",
      modelId: "opus-5.5",
      version: 1,
      accentIndex: 2,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    appRef.update({
      htmlCode: "<!DOCTYPE html><html><body><h1>Tracker v2</h1></body></html>",
      lastPrompt: "Add dark mode",
      version: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("userApps").get()
  );

  // Cross-user isolation & invalid version checks
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("userApps").get());
  await assertFails(
    appRef.update({
      version: 0,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertSucceeds(appRef.delete());
});

test("BugReport: reporter isolation, spoofing guards, email_verified admin inbox, and terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", { email: "anugrah.business.2.3@gmail.com", email_verified: false }).firestore();
  const ownerDb = testEnv.authenticatedContext("owner_uid", { email: "anugrah.business.2.3@gmail.com", email_verified: true }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const now = new Date(Date.now() - 2000);
  const aliceReportRef = aliceDb.collection("bugReports").doc("bug_1");

  // Unauthenticated cannot read or write
  await assertFails(unauthDb.collection("bugReports").get());

  // Reporter cannot create pre-FIXED report
  await assertFails(
    aliceDb.collection("bugReports").doc("bug_bad_status").set({
      userId: ALICE_UID,
      reporterName: "Alice",
      reporterEmail: "alice@example.com",
      category: "AI_CHAT",
      title: "Fake fixed bug",
      description: "Trying to bypass initial status",
      diagnostics: "sdk=36",
      status: "FIXED",
      ownerNote: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid create by Alice
  await assertSucceeds(
    aliceReportRef.set({
      userId: ALICE_UID,
      reporterName: "Alice",
      reporterEmail: "alice@example.com",
      category: "YOUR_APP",
      title: "Button animation stutter",
      description: "Observed slight stutter when opening preview.",
      diagnostics: "model=gemini-3.8-flash",
      status: "OPEN",
      ownerNote: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice can list ONLY her own reports with whereEqualTo("userId", ALICE_UID)
  await assertSucceeds(
    aliceDb.collection("bugReports").where("userId", "==", ALICE_UID).get()
  );

  // Bob cannot list all reports or read Alice's report (PII isolation)
  await assertFails(bobDb.collection("bugReports").get());
  await assertFails(bobDb.collection("bugReports").doc("bug_1").get());

  // Unverified admin email spoof MUST fail
  await assertFails(spoofedAdminDb.collection("bugReports").get());
  await assertFails(
    spoofedAdminDb.collection("bugReports").doc("bug_1").update({
      status: "FIXED",
      ownerNote: "Spoofed",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Alice cannot change her own status or ownerNote
  await assertFails(
    aliceReportRef.update({
      status: "FIXED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Verified owner (anugrah.business.2.3@gmail.com) can list all bug reports and mark FIXED with ownerNote
  await assertSucceeds(ownerDb.collection("bugReports").get());
  await assertSucceeds(
    ownerDb.collection("bugReports").doc("bug_1").update({
      status: "FIXED",
      ownerNote: "Fixed animation easing in latest update.",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Terminal state lock: Alice cannot edit report once FIXED
  await assertFails(
    aliceReportRef.update({
      title: "Editing after fixed",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Owner can clean up
  await assertSucceeds(ownerDb.collection("bugReports").doc("bug_1").delete());
});

test("UserProfile & Owner All-User Data Access: regular users isolated, verified owner accesses all users' data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", { email: "anugrah.business.2.3@gmail.com", email_verified: false }).firestore();
  const ownerDb = testEnv.authenticatedContext("owner_uid", { email: "anugrah.business.2.3@gmail.com", email_verified: true }).firestore();

  const now = new Date(Date.now() - 3000);

  // Alice creates her UserProfile, ChatThread, and UserApp
  await assertSucceeds(
    aliceDb.collection("userProfiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      photoUrl: "",
      lastModelId: "gemini-3.8-flash",
      chatCount: 1,
      appCount: 1,
      sessionStatus: "SIGNED_IN",
      createdAt: now,
      lastActiveAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("userProfiles").doc(ALICE_UID).update({
      sessionStatus: "SIGNED_OUT",
      lastActiveAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_a1").set({
      userId: ALICE_UID,
      title: "Alice Chat",
      modelId: "gemini-3.8-flash",
      stylePreset: "BALANCED",
      thinkingEnabled: false,
      pinned: false,
      lastMessagePreview: "Hi Zenvix",
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("userApps").doc("app_a1").set({
      userId: ALICE_UID,
      name: "Alice Notes App",
      description: "Notes widget",
      htmlCode: "<h1>Notes</h1>",
      lastPrompt: "Make a notes app",
      modelId: "opus-5.5",
      version: 1,
      accentIndex: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular users (Bob) CANNOT list all profiles, read Alice's profile, or read Alice's apps/chats
  await assertFails(bobDb.collection("userProfiles").get());
  await assertFails(bobDb.collection("userProfiles").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("userApps").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").get());

  // Unverified spoofed owner MUST fail
  await assertFails(spoofedAdminDb.collection("userProfiles").get());
  await assertFails(spoofedAdminDb.collection("users").doc(ALICE_UID).collection("chats").get());

  // Verified Owner (anugrah.business.2.3@gmail.com) can list all userProfiles and read Alice's chats & apps
  await assertSucceeds(ownerDb.collection("userProfiles").get());
  await assertSucceeds(ownerDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertSucceeds(ownerDb.collection("users").doc(ALICE_UID).collection("userApps").get());
  await assertSucceeds(ownerDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertSucceeds(ownerDb.collection("users").doc(ALICE_UID).collection("artifacts").get());
});

test("AuthEvent: users log their own LOGIN/LOGOUT events without spoofing, only verified owner can read/list", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", { email: "anugrah.business.2.3@gmail.com", email_verified: false }).firestore();
  const ownerDb = testEnv.authenticatedContext("owner_uid", { email: "anugrah.business.2.3@gmail.com", email_verified: true }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);

  // Alice can record LOGIN & LOGOUT events for herself with matching email
  const loginRef = aliceDb.collection("authEvents").doc("evt_login_1");
  await assertSucceeds(
    loginRef.set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      eventType: "LOGIN",
      deviceInfo: "Android 16 (SDK 36)",
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("authEvents").doc("evt_logout_1").set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      eventType: "LOGOUT",
      deviceInfo: "Android 16 (SDK 36)",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Spoofing another UID or another email MUST fail
  await assertFails(
    aliceDb.collection("authEvents").doc("evt_spoof_uid").set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      eventType: "LOGIN",
      deviceInfo: "Android",
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("authEvents").doc("evt_spoof_email").set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "bob@example.com",
      eventType: "LOGIN",
      deviceInfo: "Android",
      createdAt: now,
    })
  );

  // Regular users & unauthenticated & unverified spoofed owner CANNOT read or tamper with authEvents
  await assertFails(unauthDb.collection("authEvents").get());
  await assertFails(aliceDb.collection("authEvents").get());
  await assertFails(bobDb.collection("authEvents").doc("evt_login_1").get());
  await assertFails(spoofedAdminDb.collection("authEvents").get());
  await assertFails(loginRef.update({ eventType: "LOGOUT" }));
  await assertFails(loginRef.delete());

  // Verified owner (anugrah.business.2.3@gmail.com) can list & read all authEvents
  await assertSucceeds(ownerDb.collection("authEvents").get());
  await assertSucceeds(ownerDb.collection("authEvents").doc("evt_login_1").get());
  await assertSucceeds(ownerDb.collection("authEvents").doc("evt_login_1").delete());
});



