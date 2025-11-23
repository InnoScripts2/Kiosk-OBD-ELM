package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfservice.kiosk.ui.state.VehicleType

@Composable
fun ThicknessInputScreen(
    onNext: (VehicleType, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf<VehicleType?>(null) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    
    val isValid = selectedType != null && 
                  phone.length >= 10 && 
                  email.contains("@")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Толщиномер ЛКП",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Text("Выберите тип автомобиля:")
        VehicleType.values().forEach { type ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { selectedType = type },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedType == type) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(type.displayName)
                    Text("${type.price}₽", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Телефон") },
            placeholder = { Text("+7 XXX XXX-XX-XX") },
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            placeholder = { Text("example@email.com") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { selectedType?.let { onNext(it, phone, email) } },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Далее", fontSize = 18.sp)
        }
    }
}
