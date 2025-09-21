package com.example.nogracias

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.nogracias.service.BlockedNumbersManager

class MainActivity : AppCompatActivity() {

    private lateinit var etPrefix: EditText
    private lateinit var btnAdd: Button
    private lateinit var lvPrefixes: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var blockedNumbersManager: BlockedNumbersManager

    private val prefixList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar el manager
        blockedNumbersManager = BlockedNumbersManager(this)

        // Conectar vistas
        etPrefix = findViewById(R.id.etPrefix)
        btnAdd = findViewById(R.id.btnAdd)
        lvPrefixes = findViewById(R.id.lvPrefixes)

        // Configurar adapter
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, prefixList)
        lvPrefixes.adapter = adapter

        // Cargar prefijos guardados
        loadPrefixes()

        // Configurar botón agregar
        btnAdd.setOnClickListener {
            addPrefix()
        }

        // Configurar eliminación al hacer click largo
        lvPrefixes.setOnItemLongClickListener { _, _, position, _ ->
            removePrefix(position)
            true
        }
    }

    private fun addPrefix() {
        val prefixText = etPrefix.text.toString().trim()

        if (prefixText.isEmpty()) {
            Toast.makeText(this, "❌ Ingresa un prefijo", Toast.LENGTH_SHORT).show()
            return
        }

        // Validar que solo contenga números
        if (!prefixText.matches(Regex("\\d+"))) {
            Toast.makeText(this, "❌ Solo se permiten números", Toast.LENGTH_SHORT).show()
            return
        }

        // Verificar si ya existe
        if (prefixList.contains(prefixText)) {
            Toast.makeText(this, "⚠️ El prefijo ya existe", Toast.LENGTH_SHORT).show()
            return
        }

        // Agregar a la lista
        prefixList.add(prefixText)
        adapter.notifyDataSetChanged()

        // Guardar en SharedPreferences
        savePrefixes()

        // Limpiar campo
        etPrefix.text.clear()

        Toast.makeText(this, "✅ Prefijo '$prefixText' agregado", Toast.LENGTH_SHORT).show()
    }

    private fun removePrefix(position: Int) {
        val removedPrefix = prefixList[position]
        prefixList.removeAt(position)
        adapter.notifyDataSetChanged()

        // Guardar cambios
        savePrefixes()

        Toast.makeText(this, "🗑️ Prefijo '$removedPrefix' eliminado", Toast.LENGTH_SHORT).show()
    }

    private fun savePrefixes() {
        // Guardar en el manager para que el servicio pueda accederlos
        blockedNumbersManager.savePrefixes(prefixList)
    }

    private fun loadPrefixes() {
        // Cargar desde el manager
        val savedPrefixes = blockedNumbersManager.getPrefixes()
        prefixList.clear()
        prefixList.addAll(savedPrefixes)
        adapter.notifyDataSetChanged()
    }
}