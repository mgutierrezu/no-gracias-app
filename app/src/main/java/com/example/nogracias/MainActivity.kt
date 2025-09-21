package com.example.nogracias

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.nogracias.model.PrefixItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var etPrefix: EditText
    private lateinit var btnAdd: Button
    private lateinit var switchMain: Switch
    private lateinit var tvStatus: TextView
    private lateinit var lvPrefixes: ListView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: PrefixListAdapter
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private val prefixList = mutableListOf<PrefixItem>()
    private var isBlockingEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListView()
        loadData()
        setupListeners()
        updateUI()
    }

    private fun initViews() {
        prefs = getSharedPreferences("nogracias_prefs", Context.MODE_PRIVATE)

        etPrefix = findViewById(R.id.etPrefix)
        btnAdd = findViewById(R.id.btnAdd)
        switchMain = findViewById(R.id.switchMain)
        tvStatus = findViewById(R.id.tvStatus)
        lvPrefixes = findViewById(R.id.lvPrefixes)
        tvEmpty = findViewById(R.id.tvEmpty)
    }

    private fun setupListView() {
        adapter = PrefixListAdapter(this, prefixList)
        lvPrefixes.adapter = adapter
    }

    private fun setupListeners() {
        btnAdd.setOnClickListener { addPrefix() }

        switchMain.setOnCheckedChangeListener { _, isChecked ->
            isBlockingEnabled = isChecked
            saveBlockingState()
            updateUI()
        }
    }

    private fun addPrefix() {
        val prefixText = etPrefix.text.toString().trim()

        when {
            prefixText.isEmpty() -> {
                showToast("Ingresa un prefijo")
                return
            }
            !prefixText.matches(Regex("\\d+")) -> {
                showToast("Solo números permitidos")
                return
            }
            prefixList.any { it.value == prefixText } -> {
                showToast("El prefijo ya existe")
                return
            }
        }

        val newPrefix = PrefixItem(prefixText, true)
        prefixList.add(newPrefix)
        adapter.notifyDataSetChanged()

        savePrefixes()
        etPrefix.text.clear()
        updateUI()

        showToast("Prefijo '$prefixText' agregado")
    }

    fun removePrefix(position: Int) {
        if (position in 0 until prefixList.size) {
            val removedPrefix = prefixList[position].value
            prefixList.removeAt(position)
            adapter.notifyDataSetChanged()

            savePrefixes()
            updateUI()

            showToast("Prefijo '$removedPrefix' eliminado")
        }
    }

    fun togglePrefix(position: Int, isActive: Boolean) {
        if (position in 0 until prefixList.size) {
            prefixList[position].isActive = isActive
            adapter.notifyDataSetChanged()

            savePrefixes()

            val status = if (isActive) "activado" else "desactivado"
            showToast("Prefijo $status")
        }
    }

    private fun updateUI() {
        val activeCount = prefixList.count { it.isActive }
        val totalCount = prefixList.size

        when {
            !isBlockingEnabled -> {
                tvStatus.text = "Bloqueo desactivado"
                tvStatus.setBackgroundColor(0xFFF5F5F5.toInt())
                tvStatus.setTextColor(0xFF757575.toInt())
            }
            activeCount == 0 -> {
                tvStatus.text = "Sin prefijos activos"
                tvStatus.setBackgroundColor(0xFFFFF3E0.toInt())
                tvStatus.setTextColor(0xFFFF9800.toInt())
            }
            else -> {
                tvStatus.text = "Bloqueando $activeCount de $totalCount prefijos"
                tvStatus.setBackgroundColor(0xFFF1F8E9.toInt())
                tvStatus.setTextColor(0xFF4CAF50.toInt())
            }
        }

        if (prefixList.isEmpty()) {
            lvPrefixes.visibility = ListView.GONE
            tvEmpty.visibility = TextView.VISIBLE
        } else {
            lvPrefixes.visibility = ListView.VISIBLE
            tvEmpty.visibility = TextView.GONE
        }

        btnAdd.isEnabled = isBlockingEnabled
        etPrefix.isEnabled = isBlockingEnabled

        btnAdd.alpha = if (isBlockingEnabled) 1.0f else 0.5f
        etPrefix.alpha = if (isBlockingEnabled) 1.0f else 0.5f
    }

    private fun savePrefixes() {
        try {
            // Guardar en formato complejo (para la app)
            val json = gson.toJson(prefixList)
            prefs.edit().putString("prefixes_list", json).apply()

            // IMPORTANTE: Guardar también en formato simple (para el servicio)
            val activePrefixes = prefixList.filter { it.isActive }.map { it.value }
            val simpleJson = activePrefixes.joinToString(",")
            prefs.edit().putString("prefixes", simpleJson).apply()

            android.util.Log.d("MainActivity", "Prefijos guardados:")
            android.util.Log.d("MainActivity", "- Formato complejo: $json")
            android.util.Log.d("MainActivity", "- Formato simple: $simpleJson")
            android.util.Log.d("MainActivity", "- Prefijos activos: $activePrefixes")

        } catch (e: Exception) {
            showToast("Error guardando: ${e.message}")
        }
    }

    private fun saveBlockingState() {
        prefs.edit().putBoolean("blocking_enabled", isBlockingEnabled).apply()
    }

    private fun loadData() {
        try {
            isBlockingEnabled = prefs.getBoolean("blocking_enabled", true)
            switchMain.isChecked = isBlockingEnabled

            val json = prefs.getString("prefixes_list", null)
            if (json != null) {
                val type = object : TypeToken<List<PrefixItem>>() {}.type
                val loadedPrefixes: List<PrefixItem> = gson.fromJson(json, type) ?: emptyList()
                prefixList.clear()
                prefixList.addAll(loadedPrefixes)
            }

        } catch (e: Exception) {
            showToast("Error cargando: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Adapter personalizado para ListView
    inner class PrefixListAdapter(
        private val context: Context,
        private val items: MutableList<PrefixItem>
    ) : BaseAdapter() {

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): PrefixItem = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_prefix, parent, false)

            val item = items[position]

            val tvPrefix = view.findViewById<TextView>(R.id.tvPrefix)
            val switchPrefix = view.findViewById<Switch>(R.id.switchPrefix)
            val btnDelete = view.findViewById<Button>(R.id.btnDelete)

            tvPrefix.text = item.value
            switchPrefix.isChecked = item.isActive

            if (item.isActive) {
                tvPrefix.alpha = 1.0f
                switchPrefix.thumbTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
            } else {
                tvPrefix.alpha = 0.5f
                switchPrefix.thumbTintList = android.content.res.ColorStateList.valueOf(0xFF9E9E9E.toInt())
            }

            switchPrefix.setOnCheckedChangeListener { _, isChecked ->
                togglePrefix(position, isChecked)
            }

            btnDelete.setOnClickListener {
                removePrefix(position)
            }

            return view
        }
    }
}