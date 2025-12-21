/**
 * DGP Application JavaScript
 * Contains all client-side functionality for the password generator app
 */

// Clipboard functionality
function copyToClipboard(text) {
  // Modern clipboard API
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(function() {
      showCopyFeedback();
    }).catch(function(err) {
      console.error('Failed to copy:', err);
      fallbackCopyToClipboard(text);
    });
  } else {
    fallbackCopyToClipboard(text);
  }
}

function fallbackCopyToClipboard(text) {
  // Fallback for older browsers
  var textArea = document.createElement("textarea");
  textArea.value = text;
  textArea.style.position = "fixed";
  textArea.style.top = "0";
  textArea.style.left = "0";
  textArea.style.opacity = "0";
  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();

  try {
    document.execCommand('copy');
    showCopyFeedback();
  } catch (err) {
    console.error('Fallback copy failed:', err);
    alert('Failed to copy to clipboard. Please copy manually.');
  }

  document.body.removeChild(textArea);
}

function showCopyFeedback() {
  var feedback = document.getElementById('copy-feedback');
  if (feedback) {
    feedback.classList.add('show');
    setTimeout(function() {
      feedback.classList.remove('show');
    }, 2000);
  }
}

// Password display functionality
var passwordClearTimeout;

function showPassword(password, serviceName) {
  var display = document.getElementById('password-display');
  var textEl = document.getElementById('password-text');
  var infoEl = document.getElementById('password-info');

  if (display && textEl && infoEl) {
    textEl.textContent = password;
    infoEl.textContent = 'Password for: ' + serviceName;
    display.classList.add('show');

    // Clear any existing timeout
    if (passwordClearTimeout) {
      clearTimeout(passwordClearTimeout);
    }

    // Auto-clear after 60 seconds
    passwordClearTimeout = setTimeout(function() {
      display.classList.remove('show');
      textEl.textContent = '';
    }, 60000);
  }
}

// Service search/filter functionality
function filterServices() {
  var input = document.getElementById('service-search');
  var filter = input.value.toLowerCase();
  var select = document.getElementById('service-select');
  var options = select.getElementsByTagName('option');
  var visibleCount = 0;

  for (var i = 0; i < options.length; i++) {
    var option = options[i];
    var text = option.textContent || option.innerText;

    if (text.toLowerCase().indexOf(filter) > -1 || option.value === '') {
      option.style.display = '';
      if (option.value !== '') visibleCount++;
    } else {
      option.style.display = 'none';
    }
  }

  // Update count
  var countEl = document.getElementById('service-count');
  if (filter) {
    countEl.textContent = '(showing ' + visibleCount + ' of ' + (options.length - 1) + ' services)';
  } else {
    countEl.textContent = '(' + (options.length - 1) + ' services)';
  }
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
  // Initialize service count if search input exists
  var searchInput = document.getElementById('service-search');
  if (searchInput) {
    filterServices();
    // Add event listener for search input
    searchInput.addEventListener('keyup', filterServices);
  }

  // Add event listener for copy button
  var copyButton = document.getElementById('copy-button');
  if (copyButton) {
    copyButton.addEventListener('click', function() {
      var passwordText = document.getElementById('password-text');
      if (passwordText) {
        copyToClipboard(passwordText.textContent);
      }
    });
  }

  // Auto-trigger password display if password exists
  var passwordText = document.getElementById('password-text');
  var passwordInfo = document.getElementById('password-info');
  if (passwordText && passwordText.textContent.trim() && 
      passwordInfo && passwordInfo.textContent.trim()) {
    var password = passwordText.textContent.trim();
    var serviceMatch = passwordInfo.textContent.match(/Password for: (.+)/);
    var serviceName = serviceMatch ? serviceMatch[1] : '';
    if (password && serviceName) {
      showPassword(password, serviceName);
    }
  }
});
