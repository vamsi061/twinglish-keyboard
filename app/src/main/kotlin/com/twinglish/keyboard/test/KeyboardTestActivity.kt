package com.twinglish.keyboard.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Internal test screen: exercise the IME against every editor type without
 * leaving the app. Useful for checking password handling, enter actions,
 * number layouts, etc.
 */
class KeyboardTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                @OptIn(ExperimentalMaterial3Api::class)
                Scaffold(topBar = { TopAppBar(title = { Text("Keyboard Test") }) }) { padding ->
                    TestFields(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun TestFields(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        var normal by rememberSaveable { mutableStateOf("") }
        var multiline by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var email by rememberSaveable { mutableStateOf("") }
        var url by rememberSaveable { mutableStateOf("") }
        var search by rememberSaveable { mutableStateOf("") }
        var number by rememberSaveable { mutableStateOf("") }
        var phone by rememberSaveable { mutableStateOf("") }

        FieldLabel("Normal text — try: \"What are you doing?\"")
        OutlinedTextField(value = normal, onValueChange = { normal = it }, modifier = Modifier.fillMaxSize(), singleLine = true)
        Spacer(Modifier.height(8.dp))

        FieldLabel("Multiline")
        OutlinedTextField(value = multiline, onValueChange = { multiline = it }, modifier = Modifier.fillMaxSize(), minLines = 3)
        Spacer(Modifier.height(8.dp))

        FieldLabel("Password (Twinglish must stay disabled)")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxSize(),
        )
        Spacer(Modifier.height(8.dp))

        FieldLabel("Email")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxSize(),
        )
        Spacer(Modifier.height(8.dp))

        FieldLabel("URL")
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxSize(),
        )
        Spacer(Modifier.height(8.dp))

        FieldLabel("Search")
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxSize(),
        )
        Spacer(Modifier.height(8.dp))

        FieldLabel("Number")
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxSize(),
        )
        Spacer(Modifier.height(8.dp))

        FieldLabel("Phone")
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxSize(),
        )
        Spacer(Modifier.height(16.dp))

        Text(
            "Switch to Twinglish Keyboard via the toolbar globe, or long-press space.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(4.dp))
}
