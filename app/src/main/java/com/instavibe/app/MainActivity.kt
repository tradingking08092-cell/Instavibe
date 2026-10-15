package com.instavibe.app

import android.net.Uri
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

private val bg = Color(0xFF08080C)
private val panel = Color(0xFF17171E)
private val purple = Color(0xFFB58AFF)
private val white = Color(0xFFF5F2FA)
private val auth get() = FirebaseAuth.getInstance()
private val db get() = FirebaseFirestore.getInstance()
private val storage get() = FirebaseStorage.getInstance()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent {
        MaterialTheme(colorScheme = darkColorScheme(primary = purple, background = bg, surface = panel, onSurface = white)) { InstaApp() }
    } }
}

data class Post(val id: String = "", val uid: String = "", val author: String = "", val caption: String = "", val mediaUrl: String = "", val mediaType: String = "image", val createdAt: Long = 0)
data class UserProfile(val uid: String = "", val name: String = "", val bio: String = "")

@Composable fun InstaApp() {
    var user by remember { mutableStateOf(auth.currentUser) }
    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Scaffold(containerColor = bg, bottomBar = {
        if (user != null) NavigationBar(containerColor = panel) {
            listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.AddCircle, Icons.Default.Person).forEachIndexed { i, icon ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(listOf("Feed", "Explore", "Post", "Profile")[i]) })
            }
        }
    }) { padding -> Column(Modifier.fillMaxSize().padding(padding)) {
        if (user == null) AuthScreen(onSuccess = { user = auth.currentUser }, onError = { message = it })
        else when(tab) {
            0 -> FeedScreen(explore = false)
            1 -> FeedScreen(explore = true)
            2 -> CreateScreen(onDone = { tab = 0 }, onError = { message = it })
            3 -> ProfileScreen(onLogout = { auth.signOut(); user = null }, onError = { message = it })
        }
        if (message.isNotBlank()) AlertDialog(onDismissRequest = { message = "" }, confirmButton = { TextButton(onClick = { message = "" }) { Text("OK") } }, title = { Text("InstaVibe") }, text = { Text(message) })
    } }
}

@Composable fun AuthScreen(onSuccess: () -> Unit, onError: (String) -> Unit) {
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var signup by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center) {
        Text("InstaVibe", style = MaterialTheme.typography.displayMedium, color = purple)
        Text("Capture. Connect. Vibe.", color = Color.Gray)
        Spacer(Modifier.height(30.dp))
        if (signup) OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password (6+ characters)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(enabled = !busy, onClick = { scope.launch { busy = true; try {
            if (signup) {
                require(name.isNotBlank() && password.length >= 6) { "Enter a name and password of at least 6 characters" }
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val uid = requireNotNull(result.user?.uid)
                db.collection("users").document(uid).set(UserProfile(uid, name.trim(), "")).await()
                auth.currentUser?.sendEmailVerification()?.await()
            } else auth.signInWithEmailAndPassword(email.trim(), password).await()
            onSuccess()
        } catch (e: Exception) { onError(e.localizedMessage ?: "Authentication failed") } finally { busy = false } } }, modifier = Modifier.fillMaxWidth()) { Text(if(signup) "Create account" else "Log in") }
        TextButton(onClick = { signup = !signup }) { Text(if(signup) "Already registered? Log in" else "New here? Sign up") }
        TextButton(onClick = { if(email.isBlank()) onError("Enter your email first") else scope.launch { try { auth.sendPasswordResetEmail(email.trim()).await(); onError("Password reset email sent") } catch(e: Exception) { onError(e.localizedMessage ?: "Could not send email") } } }) { Text("Forgot password?") }
    }
}

@Composable fun FeedScreen(explore: Boolean) {
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }; var search by remember { mutableStateOf("") }
    DisposableEffect(explore) {
        val reg = db.collection("posts").orderBy("createdAt", Query.Direction.DESCENDING).limit(100).addSnapshotListener { snap, _ -> posts = snap?.documents?.mapNotNull { it.toObject(Post::class.java)?.copy(id = it.id) } ?: emptyList() }
        onDispose { reg.remove() }
    }
    Column {
        Text(if(explore) "Explore" else "InstaVibe", style = MaterialTheme.typography.headlineMedium, color = purple, modifier = Modifier.padding(18.dp))
        if (explore) OutlinedTextField(search, { search = it }, label = { Text("Search captions or creators") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        val filtered = if(search.isBlank()) posts else posts.filter { it.caption.contains(search, true) || it.author.contains(search, true) }
        LazyColumn { items(filtered, key = { it.id }) { PostCard(it) } }
    }
}

@Composable fun PostCard(post: Post) {
    val uid = auth.currentUser?.uid ?: return
    val context = LocalContext.current
    var liked by remember(post.id) { mutableStateOf(false) }; var likes by remember(post.id) { mutableIntStateOf(0) }; var following by remember(post.uid) { mutableStateOf(false) }
    var comments by remember(post.id) { mutableStateOf<List<Pair<String,String>>>(emptyList()) }; var comment by remember(post.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    DisposableEffect(post.id, uid) {
        val l = db.collection("posts").document(post.id).collection("likes").addSnapshotListener { s, _ -> likes = s?.size() ?: 0; liked = s?.documents?.any { it.id == uid } == true }
        val c = db.collection("posts").document(post.id).collection("comments").orderBy("createdAt").limit(30).addSnapshotListener { s, _ -> comments = s?.documents?.map { (it.getString("author") ?: "User") to (it.getString("text") ?: "") } ?: emptyList() }
        val f = db.collection("users").document(uid).collection("following").document(post.uid).addSnapshotListener { s, _ -> following = s?.exists() == true }
        onDispose { l.remove(); c.remove(); f.remove() }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).background(panel, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(post.author.ifBlank { "Creator" }, color = white, modifier = Modifier.weight(1f))
            if(uid != post.uid) TextButton(onClick = { scope.launch { val ref = db.collection("users").document(uid).collection("following").document(post.uid); if(following) ref.delete().await() else ref.set(mapOf("createdAt" to System.currentTimeMillis())).await() } }) { Text(if(following) "Following" else "Follow") }
        }
        if(post.mediaType == "image") AsyncImage(model = post.mediaUrl, contentDescription = "Post image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(280.dp).background(bg, RoundedCornerShape(12.dp)))
        else Text("▶ Play video", modifier = Modifier.fillMaxWidth().padding(24.dp).clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(post.mediaUrl)).apply { setDataAndType(Uri.parse(post.mediaUrl), "video/*") }) }, color = purple)
        Text(post.caption, modifier = Modifier.padding(vertical = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch { val ref = db.collection("posts").document(post.id).collection("likes").document(uid); if(liked) ref.delete().await() else ref.set(mapOf("createdAt" to System.currentTimeMillis())).await() } }) { Icon(if(liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if(liked) purple else white) }
            Text("$likes likes")
        }
        comments.forEach { (author, text) -> Text("$author: $text", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(3.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(comment, { comment = it }, label = { Text("Add comment") }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(enabled = comment.isNotBlank(), onClick = { val value = comment.trim(); comment = ""; scope.launch { db.collection("posts").document(post.id).collection("comments").add(mapOf("uid" to uid, "author" to (auth.currentUser?.email?.substringBefore('@') ?: "User"), "text" to value, "createdAt" to System.currentTimeMillis())).await() } }) { Icon(Icons.Default.Send, "Send") }
        }
    }
}

@Composable fun CreateScreen(onDone: () -> Unit, onError: (String) -> Unit) {
    var caption by remember { mutableStateOf("") }; var uri by remember { mutableStateOf<Uri?>(null) }; var type by remember { mutableStateOf("image") }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked -> uri = picked; if(picked != null) type = if(picked.toString().contains("video")) "video" else "image" }
    Column(Modifier.padding(20.dp)) {
        Text("New post", style = MaterialTheme.typography.headlineMedium)
        Row { Button(onClick = { type = "image"; picker.launch("image/*") }) { Text("Choose photo") }; Spacer(Modifier.width(8.dp)); Button(onClick = { type = "video"; picker.launch("video/*") }) { Text("Choose video") } }
        Text(uri?.lastPathSegment ?: "No media selected")
        OutlinedTextField(caption, { caption = it }, label = { Text("Caption") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(enabled = uri != null && !busy, onClick = { scope.launch { busy = true; try {
            val uid = requireNotNull(auth.currentUser?.uid)
            val selected = requireNotNull(uri)
            val ext = if(type == "video") "mp4" else "jpg"
            val ref = storage.reference.child("posts/$uid/${UUID.randomUUID()}.$ext")
            ref.putFile(selected).await()
            val url = ref.downloadUrl.await().toString()
            val author = db.collection("users").document(uid).get().await().getString("name") ?: auth.currentUser?.email?.substringBefore('@') ?: "Creator"
            val id = db.collection("posts").document().id
            db.collection("posts").document(id).set(Post(id, uid, author, caption.take(2000), url, type, System.currentTimeMillis())).await()
            caption = ""; uri = null; onDone()
        } catch(e: Exception) { onError(e.localizedMessage ?: "Upload failed") } finally { busy = false } } }) { Text(if(busy) "Uploading..." else "Publish") }
    }
}

@Composable fun ProfileScreen(onLogout: () -> Unit, onError: (String) -> Unit) {
    val uid = auth.currentUser?.uid ?: return
    var name by remember { mutableStateOf("") }; var bio by remember { mutableStateOf("") }; var count by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    DisposableEffect(uid) {
        val a = db.collection("users").document(uid).addSnapshotListener { s, _ -> name = s?.getString("name") ?: ""; bio = s?.getString("bio") ?: "" }
        val b = db.collection("posts").whereEqualTo("uid", uid).addSnapshotListener { s, _ -> count = s?.size() ?: 0 }
        onDispose { a.remove(); b.remove() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(84.dp), tint = purple)
        Text(auth.currentUser?.email ?: "", color = Color.LightGray)
        Text("$count posts", color = purple)
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { scope.launch { try { db.collection("users").document(uid).set(UserProfile(uid, name.take(60), bio.take(250))).await(); onError("Profile saved") } catch(e: Exception) { onError(e.localizedMessage ?: "Save failed") } } }) { Text("Save profile") }
        Text("Email verified: ${if(auth.currentUser?.isEmailVerified == true) "Yes" else "No"}")
        TextButton(onClick = { scope.launch { try { auth.currentUser?.sendEmailVerification()?.await(); onError("Verification email sent") } catch(e: Exception) { onError(e.localizedMessage ?: "Failed") } } }) { Text("Send verification email") }
        OutlinedButton(onClick = onLogout) { Text("Sign out") }
    }
}
