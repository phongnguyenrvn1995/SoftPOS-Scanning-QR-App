package com.example.softposqr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.softposqr.databinding.ActivityMainBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions


class MainActivity : AppCompatActivity() {
    var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        binding!!.btnScan.setOnClickListener { startScanning() }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 9999)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 9999)
            return
        if (grantResults.first() != PackageManager.PERMISSION_GRANTED)
            finish()
    }

    // Register the launcher and result handler
    private val barcodeLauncher = registerForActivityResult<ScanOptions, ScanIntentResult>(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this@MainActivity, "Cancelled", Toast.LENGTH_LONG).show()
        } else {
            Log.d("QR TAG", result.contents)
            analyseQRString(result.contents)
        }
    }

    private fun startScanning() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
        options.setPrompt("Scan a barcode")
        options.setCameraId(0) // Use a specific camera of the device

        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        barcodeLauncher.launch(options)
    }

    private fun analyseQRString(qrString: String) {
//        val qrString = "00020101021238560010A0000007270126000666888801121050000040440208QRIBFTTA530370454065555555802VN62290825KVNQR202310290755121283946304D6F6"
        val intent = Intent(applicationContext, ConfirmActivity::class.java)
        intent.putExtra("qrString", qrString)
        intent.putExtra("authorization", binding!!.txtApiAuthorization.text.toString())
        intent.putExtra("baseURL", binding!!.txtApiUrl.text.toString())
        intent.putExtra("apiPath", binding!!.txtApiPath.text.toString())
        startActivity(intent)
    }
}