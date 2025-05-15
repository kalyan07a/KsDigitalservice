
        document.addEventListener('DOMContentLoaded', function() {
            const stepsModal = document.getElementById('stepsModal');
            const closeBtn = document.getElementById('closeStepsModal');

            // Show modal
            stepsModal.style.display = 'flex';

            // Close modal handlers
            closeBtn.addEventListener('click', () => {
                stepsModal.style.display = 'none';
            });

            stepsModal.addEventListener('click', (e) => {
                if(e.target === stepsModal) {
                    stepsModal.style.display = 'none';
                }
            });
        });

    document.addEventListener('DOMContentLoaded', (event) => {

        // --- PDF.js Setup (Non-Module) ---
        if (typeof pdfjsLib === 'undefined') {
            console.error("PDF.js library not loaded!");
            alert("Error: Could not load PDF viewer library. PDF previews may not work.");
        } else {
            try {
                 pdfjsLib.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js`;
                 console.log("PDF.js worker configured (non-module).");
             } catch (pdfError) {
                 console.error("Error setting PDF.js worker source:", pdfError);
                 alert("Error initializing PDF viewer library.");
             }
        }

        // --- Global State & Config ---
        const MAX_FILES = 10;
        let fileItems = [];
        let nextItemId = 0;

        // --- DOM Elements ---
        const fileInput = document.getElementById('fileInput');
        const addFileButtonLabel = document.getElementById('addFileButtonLabel');
        const fileListContainer = document.getElementById('fileListContainer');
        const fileItemTemplate = document.getElementById('fileItemTemplate');
        const fileCounter = document.getElementById('fileCounter');
        const totalPriceDisplay = document.getElementById('totalPriceDisplay');
        const payButton = document.getElementById('pay-button');
        const globalStatusMessage = document.getElementById('globalStatusMessage');
        const paymentLoader = document.getElementById('paymentLoader');
        const previewModal = document.getElementById('previewModal');
        const modalPreviewContent = document.getElementById('modalPreviewContent');
        const modalPreviewTitle = document.getElementById('previewModalTitle');
        const closePreviewModal = document.getElementById('closePreviewModal');
        const printerId = document.querySelector('p span').textContent.trim();

        // --- Event Listeners ---
        addFileButtonLabel.addEventListener('click', (e) => {
            if (addFileButtonLabel.style.opacity === '0.6' || fileInput.disabled) {
                console.log('Add file button clicked but is disabled. Preventing default.');
                e.preventDefault();
            }
            // No explicit fileInput.click() needed here
        });
        fileInput.addEventListener('change', handleFileSelect);
        fileListContainer.addEventListener('change', handleOptionChange);
        fileListContainer.addEventListener('click', handleItemAction);
        fileListContainer.addEventListener('input', handleNumberInputChange);
        payButton.addEventListener('click', initiatePayment);
        closePreviewModal.addEventListener('click', closePreviewModalHandler);
        previewModal.addEventListener('click', (event) => {
            if (event.target === previewModal) { closePreviewModalHandler(); }
        });

        // --- Initialization ---
        function initializeApp() {
            console.log('App Initializing...');
            updateFileCounter();
            updateTotalPriceDisplay();
            hideGlobalStatus();
        }

        // --- Helper Functions ---
        function showLoader(loaderElement) { if(loaderElement) loaderElement.style.display = 'block'; }
        function hideLoader(loaderElement) { if(loaderElement) loaderElement.style.display = 'none'; }
        function showGlobalStatus(message, type = 'info') {
            globalStatusMessage.textContent = message;
            globalStatusMessage.className = ''; // Clear previous
            globalStatusMessage.classList.add(`status-${type}`);
            globalStatusMessage.style.display = 'block';
            console.log(`Global Status (${type}):`, message);
        }
        function hideGlobalStatus() {
            globalStatusMessage.textContent = '';
            globalStatusMessage.className = '';
            globalStatusMessage.style.display = 'none';
        }
        function showItemStatus(itemElement, message, type = 'info') {
            const statusEl = itemElement?.querySelector('.item-status-display');
            if (statusEl) {
                statusEl.textContent = message;
                statusEl.className = 'item-status-display'; // Reset
                statusEl.classList.add(type);
                statusEl.style.display = 'block';
                console.log(`Item Status (ID: ${itemElement.dataset.id}, Type: ${type}):`, message);
            }
        }
        function hideItemStatus(itemElement) {
            const statusEl = itemElement?.querySelector('.item-status-display');
            if (statusEl) {
                statusEl.textContent = '';
                statusEl.className = 'item-status-display';
                statusEl.style.display = 'none';
            }
        }
        function formatFileName(fileName, maxLength = 30) {
            if (!fileName) return 'Processing...';
            if (fileName.length > maxLength) {
                const parts = fileName.split('.');
                const ext = parts.length > 1 ? '.' + parts.pop() : '';
                const nameWithoutExt = parts.join('.');
                return nameWithoutExt.substring(0, maxLength - 3 - ext.length) + '...' + ext;
            }
            return fileName;
        }
        function findItemById(id) { return fileItems.find(item => item.id === id); }
        function getItemElementById(id) { return fileListContainer.querySelector(`.file-item-container[data-id="${id}"]`); }
        function updateFileCounter() {
            const count = fileItems.length;
            fileCounter.textContent = `${count} / ${MAX_FILES} files selected.`;
            const isDisabled = count >= MAX_FILES;
            addFileButtonLabel.style.opacity = isDisabled ? '0.6' : '1';
            addFileButtonLabel.style.cursor = isDisabled ? 'not-allowed' : 'pointer';
            fileInput.disabled = isDisabled;
        }

        // --- Pricing Logic (Aggregated) ---
        /**
         * Calculates the price for aggregated B&W pages based on new tiers.
         * @param {number} totalPages - Total B&W pages * copies.
         * @returns {number} Price in Rupees.
         */
        function calculateBwTieredPrice(totalPages) {
            if (totalPages <= 0) return 0;
            // B&W: <=5 pages -> 10 Rs/page. >5 pages -> 50 Rs (for first 5) + 5 Rs/page (for rest)
            return (totalPages <= 2) ? totalPages * 10 : 20 + (totalPages - 2) * 5;
        }
        /**
         * Calculates the price for aggregated Color pages based on new tiers.
         * @param {number} totalPages - Total Color pages * copies.
         * @returns {number} Price in Rupees.
         */
        function calculateColorTieredPrice(totalPages) {
             if (totalPages <= 0) return 0;
             // Color: <=3 pages -> 15 Rs/page. >3 pages -> 45 Rs (for first 3) + 8 Rs/page (for rest)
            return (totalPages <= 2) ? totalPages * 15 : 30 + (totalPages - 2) * 9;
        }

        /** Calculates the overall total price by aggregating pages based on selected print type. */
        function calculateAggregatedTotalPrice(currentFileItems) {
            let totalBwPageInteractions = 0;
            let totalColorPageInteractions = 0;
            currentFileItems.forEach(item => {
                if (item.status === 'processed' && item.fileInfo && item.fileInfo.pageCount >= 0) {
                    // Check if the required URL/filename exists for the *selected* print type
                    const requiredUrl = (item.printType === 1) ? item.fileInfo.c_url : item.fileInfo.url;
                    const requiredFileName = (item.printType === 1) ? item.fileInfo.fileName : item.fileInfo.bwFileName;
                    if (requiredUrl && requiredFileName) {
                        const pageInteractions = item.fileInfo.pageCount * item.numberOfCopies;
                        if (item.printType === 0) { // B&W Selected
                             totalBwPageInteractions += pageInteractions;
                        } else { // Color Selected
                             totalColorPageInteractions += pageInteractions;
                        }
                    }
                    // else: Don't count pages if the selected print type is unavailable (e.g., selected B&W but no B&W file processed)
                }
            });
            console.log(`Aggregated Pages For Pricing -> B&W: ${totalBwPageInteractions}, Color: ${totalColorPageInteractions}`);
            const totalBwPrice = calculateBwTieredPrice(totalBwPageInteractions);
            const totalColorPrice = calculateColorTieredPrice(totalColorPageInteractions);
            const finalTotal = totalBwPrice + totalColorPrice;

            console.log(`Aggregated Price -> B&W: Rs. ${totalBwPrice}, Color: Rs. ${totalColorPrice}, Total: Rs. ${finalTotal}`);
            return finalTotal;
        }


        // --- Modified UI Update Functions ---

        // REMOVED updateItemPriceDisplay function

        // --- START OF UPDATED updateTotalPriceDisplay FUNCTION ---
        function updateTotalPriceDisplay() {
            let total = 0; // Will be recalculated using the dedicated function
            let grandTotalPages = 0; // New variable to store total pages * copies
            let canPay = false;
            let readyItemCount = 0;
            let hasPendingOrError = false;

            // Iterate through items to calculate total pages and check readiness
            fileItems.forEach(item => {
                let isItemReadyForPricingAndCounting = false;
                if (item.status === 'processed' && item.fileInfo && item.fileInfo.pageCount >= 0) {
                    const requiredUrl = (item.printType === 1) ? item.fileInfo?.c_url : item.fileInfo?.url;
                    const requiredFileName = (item.printType === 1) ? item.fileInfo?.fileName : item.fileInfo?.bwFileName;
                    if (requiredUrl && requiredFileName) {
                        readyItemCount++;
                        isItemReadyForPricingAndCounting = true;
                         // Calculate pages for this item (page count * copies)
                        const pageInteractions = item.fileInfo.pageCount * item.numberOfCopies;
                        grandTotalPages += pageInteractions; // Add to the grand total
                    } else {
                        // Processed but missing a required URL/filename for the selected type
                        hasPendingOrError = true;
                    }
                } else if (item.status !== 'processed') {
                    // Still processing or errored
                    hasPendingOrError = true;
                }
                // Note: We only add pages to grandTotalPages if the item is fully ready for its selected print type
            });

            // Calculate the final monetary total using the existing aggregation logic
            // It internally checks for available URLs/filenames based on selected printType per item
            total = calculateAggregatedTotalPrice(fileItems);

            // Determine if payment is possible (same logic as before)
            const allZeroPageProcessed = fileItems.length > 0 && fileItems.every(i => i.status === 'processed' && i.fileInfo?.pageCount === 0);
            // Item is considered 'payable' if processed AND the selected print type is available
             const payableItemCount = fileItems.filter(item => {
                if (item.status !== 'processed' || !item.fileInfo || item.fileInfo.pageCount < 0) return false;
                const requiredUrl = (item.printType === 1) ? item.fileInfo.c_url : item.fileInfo.url;
                const requiredFileName = (item.printType === 1) ? item.fileInfo.fileName : item.fileInfo.bwFileName;
                return requiredUrl && requiredFileName;
            }).length;

            // Allow payment if there's at least one payable item and no pending/error items, OR if all items are zero-page processed
            canPay = (payableItemCount > 0 && !hasPendingOrError && fileItems.length > 0 && total >= 0) || (total === 0 && allZeroPageProcessed && !hasPendingOrError);


            // Update the display text based on the state
            if (fileItems.length === 0) {
                totalPriceDisplay.textContent = '';
            } else if (hasPendingOrError) {
                // Check if *any* item is still processing or has an error status
                const isProcessing = fileItems.some(i => i.status === 'processing');
                const hasError = fileItems.some(i => i.status === 'error');
                const hasUnavailableSelection = fileItems.some(item => {
                     if (item.status !== 'processed' || !item.fileInfo) return false; // Only check processed items
                     const requiredUrl = (item.printType === 1) ? item.fileInfo.c_url : item.fileInfo.url;
                     const requiredFileName = (item.printType === 1) ? item.fileInfo.fileName : item.fileInfo.bwFileName;
                     return !requiredUrl || !requiredFileName;
                 });

                 if (isProcessing) {
                     totalPriceDisplay.textContent = 'Processing... Details unavailable.';
                 } else if (hasError) {
                     totalPriceDisplay.textContent = 'Some files have errors. Details unavailable.';
                 } else if (hasUnavailableSelection) {
                      totalPriceDisplay.textContent = 'Some selections unavailable. Details pending.';
                 }
                 else {
                     totalPriceDisplay.textContent = 'Calculating... Details unavailable.'; // Fallback
                 }

            } else if (payableItemCount === 0 && !allZeroPageProcessed) { // Ensure 0-page files don't show this
                totalPriceDisplay.textContent = 'No valid items ready for printing.'; // E.g., only items where selected type is unavailable
            } else {
                // **** THIS IS THE CORE CHANGE ****
                // Display the new format: Total pages and total amount
                totalPriceDisplay.textContent = `Total pages is ${grandTotalPages}, amount is Rs. ${total}`;
            }

            // Update pay button state
            payButton.disabled = !canPay;
            console.log(`updateTotalPriceDisplay: Pages=${grandTotalPages}, Amount=${total}, PayableItems=${payableItemCount}, Pending/Error=${hasPendingOrError}, CanPay=${canPay}`);
        }
         // --- END OF UPDATED updateTotalPriceDisplay FUNCTION ---


        function updateItemUIState(item) {
            const itemElement = item.element; if (!itemElement) return;
            const optionsDiv = itemElement.querySelector('.item-options');
            const previewButton = itemElement.querySelector('.preview-icon');
            const itemLoader = itemElement.querySelector('.item-loader');
            const fileNameEl = itemElement.querySelector('.file-name-display');
            fileNameEl.textContent = formatFileName(item.fileInfo?.originalFileName || item.file?.name || 'Unknown File');
            itemElement.classList.remove('processing', 'error');
            hideLoader(itemLoader);
            if (item.status === 'processing') {
                itemElement.classList.add('processing'); showLoader(itemLoader);
                optionsDiv.style.display = 'none'; previewButton.disabled = true; hideItemStatus(itemElement);
            } else if (item.status === 'error') {
                itemElement.classList.add('error'); optionsDiv.style.display = 'none'; previewButton.disabled = true;
            } else if (item.status === 'processed') {
                optionsDiv.style.display = 'flex'; previewButton.disabled = false; hideItemStatus(itemElement);
                const isImage = item.fileInfo?.mimeType?.startsWith('image/');
                const pageRangeGroup = itemElement.querySelector('.page-range');
                if (isImage) {
                    pageRangeGroup.style.display = 'none'; item.pageRangeType = 'all';
                    const allRadio = itemElement.querySelector('input[data-option="pageRangeType"][value="all"]');
                    if (allRadio) allRadio.checked = true;
                } else { pageRangeGroup.style.display = ''; updateCustomInputState(item); }
                // Update B&W radio state based on availability
                const bwRadioLabel = itemElement.querySelector('label.radio-option:has(input[value="0"][data-option="printType"])');
                const bwRadioInput = bwRadioLabel?.querySelector('input');
                if (bwRadioInput && bwRadioLabel) {
                    const bwAvailable = !!item.fileInfo?.url && !!item.fileInfo?.bwFileName;
                    bwRadioInput.disabled = !bwAvailable;
                    bwRadioLabel.style.opacity = bwAvailable ? '1' : '0.5';
                    bwRadioLabel.style.cursor = bwAvailable ? 'pointer' : 'not-allowed';
                    bwRadioLabel.title = bwAvailable ? '' : 'B&W version not available';
                    if (!bwAvailable && item.printType === 0) {
                        item.printType = 1; // Force switch to color if B&W selected but unavailable
                        const colorRadio = itemElement.querySelector('input[data-option="printType"][value="1"]');
                        if (colorRadio) colorRadio.checked = true;
                        showItemStatus(itemElement, "B&W unavailable, switched to Color.", "info");
                    }
                }
                 // Update Color radio state based on availability (c_url and fileName)
                const colorRadioLabel = itemElement.querySelector('label.radio-option:has(input[value="1"][data-option="printType"])');
                const colorRadioInput = colorRadioLabel?.querySelector('input');
                 if (colorRadioInput && colorRadioLabel) {
                     const colorAvailable = !!item.fileInfo?.c_url && !!item.fileInfo?.fileName;
                     colorRadioInput.disabled = !colorAvailable;
                     colorRadioLabel.style.opacity = colorAvailable ? '1' : '0.5';
                     colorRadioLabel.style.cursor = colorAvailable ? 'pointer' : 'not-allowed';
                     colorRadioLabel.title = colorAvailable ? '' : 'Color version not available';
                     // No automatic switch needed here, as color is the default assumption
                     if (!colorAvailable && item.printType === 1) {
                          // This state should ideally not happen if upload guarantees a color version, but good for robustness
                          showItemStatus(itemElement, "Color version processing failed or is unavailable.", "warning");
                          // Potentially disable the preview button if color is selected but unavailable
                           previewButton.disabled = true;
                      } else if (colorAvailable) {
                          // Re-enable preview button if color becomes available and is selected
                          previewButton.disabled = false;
                      }
                 }

                // Ensure preview button reflects availability of the *currently selected* type
                const currentPreviewAvailable = (item.printType === 1)
                   ? (!!item.fileInfo?.c_url && !!item.fileInfo?.fileName)
                   : (!!item.fileInfo?.url && !!item.fileInfo?.bwFileName);
                 previewButton.disabled = !currentPreviewAvailable;


                highlightSelectedRadio(itemElement, 'printType');
                highlightSelectedRadio(itemElement, 'pageRangeType');
            } else { optionsDiv.style.display = 'none'; previewButton.disabled = true; hideItemStatus(itemElement); }
            updateCopiesButtonStates(item);
            updateTotalPriceDisplay(); // Update total whenever item state changes
        }

        function updateCustomInputState(item) {
            const itemElement = item.element;
            const pageRangeGroup = itemElement.querySelector('.page-range');
            if (!itemElement || !pageRangeGroup || pageRangeGroup.style.display === 'none') return;
            const customPageDiv = itemElement.querySelector('.custom-page-inputs');
            const startInput = itemElement.querySelector('input[data-option="startPage"]');
            const endInput = itemElement.querySelector('input[data-option="endPage"]');
            const updateButton = itemElement.querySelector('.update-details-button');
            const isCustom = item.pageRangeType === 'custom';
            customPageDiv.classList.toggle('visible', isCustom);
            startInput.disabled = !isCustom; endInput.disabled = !isCustom;
            startInput.required = isCustom; endInput.required = isCustom;
            const totalPages = item.fileInfo?.pageCount;
            if (totalPages > 0) {
                startInput.placeholder = '1'; startInput.max = totalPages;
                endInput.placeholder = totalPages; endInput.max = totalPages;
            } else { startInput.placeholder = ''; startInput.max = null; endInput.placeholder = ''; endInput.max = null; }
            if (!isCustom) { startInput.value = ''; endInput.value = ''; startInput.setCustomValidity(''); endInput.setCustomValidity(''); }
            updateButton.disabled = !isCustom || !areCustomInputsValid(item);
        }

        function areCustomInputsValid(item) {
            const itemElement = item.element;
            const startInput = itemElement.querySelector('input[data-option="startPage"]');
            const endInput = itemElement.querySelector('input[data-option="endPage"]');
            if (!startInput || !endInput) return false;
            const startVal = parseInt(startInput.value, 10); const endVal = parseInt(endInput.value, 10);
            const totalPages = item.fileInfo?.pageCount;
            const basicValid = !isNaN(startVal) && !isNaN(endVal) && startVal >= 1 && endVal >= startVal;
            const withinBounds = (totalPages !== null && totalPages > 0) ? (endVal <= totalPages) : true; // Allow if pageCount unknown
            const inputsValidHTML = startInput.checkValidity() && endInput.checkValidity();
            return basicValid && withinBounds && inputsValidHTML;
        }

        function updateCopiesButtonStates(item) {
            const itemElement = item.element;
            const decrementBtn = itemElement.querySelector('button[data-action="decrementCopies"]');
            const countInput = itemElement.querySelector('input[data-option="numberOfCopies"]');
            if (decrementBtn && countInput) {
                const value = parseInt(countInput.value, 10);
                decrementBtn.disabled = isNaN(value) || value <= 1;
            }
        }

        function highlightSelectedRadio(itemElement, groupName) {
            const radios = itemElement.querySelectorAll(`input[data-option="${groupName}"]`);
            radios.forEach(radio => {
                const label = radio.closest('label.radio-option');
                if (label) { label.classList.toggle('selected', radio.checked); }
            });
        }

        // --- Event Handlers ---
        function handleFileSelect(event) {
            console.log('handleFileSelect triggered.');
            hideGlobalStatus();
            console.log('Event Target:', event.target);
            console.log('Selected files:', event.target.files);
            const files = Array.from(event.target.files || []);
            console.log(`Detected ${files.length} file(s).`);
            if (!files.length) { console.log('Exiting handleFileSelect because no files were found in the event.'); return; }
            const currentCount = fileItems.length; const availableSlots = MAX_FILES - currentCount;
            if (availableSlots <= 0) { showGlobalStatus(`Cannot add more files. Maximum ${MAX_FILES} allowed.`, 'warning'); console.log('Exiting handleFileSelect because max files limit reached.'); event.target.value = null; return; }
            if (files.length > availableSlots) { showGlobalStatus(`You can only add ${availableSlots} more file(s). Added the first ${availableSlots}.`, 'warning'); }
            const filesToAdd = files.slice(0, availableSlots);
            console.log(`Attempting to add ${filesToAdd.length} file(s).`);
            filesToAdd.forEach((file, index) => {
                console.log(`Processing file #${index + 1}: ${file.name}, Type: ${file.type}, Size: ${file.size}`);
                const allowedTypes = ['application/pdf', 'image/jpeg', 'image/png', 'image/jpg'];
                if (!allowedTypes.includes(file.type)) { showGlobalStatus(`Skipped unsupported file: ${file.name} (${file.type})`, 'warning'); console.log(`Skipping file ${file.name} due to unsupported type.`); return; }
                if (file.size > 10.5 * 1024 * 1024) { showGlobalStatus(`Skipped large file: ${file.name} (Max 10MB)`, 'warning'); console.log(`Skipping file ${file.name} due to size (${file.size}).`); return; }
                if (file.size === 0) { showGlobalStatus(`Skipped empty file: ${file.name}`, 'warning'); console.log(`Skipping file ${file.name} because size is 0.`); return; }
                console.log(`File ${file.name} passed validation. Creating item UI.`);
                const newItemId = `item-${nextItemId++}`;
                const newItem = { id: newItemId, file: file, fileInfo: null, printType: 1, pageRangeType: 'all', startPage: null, endPage: null, numberOfCopies: 1, price: 0, status: 'new', element: null };
                try {
                    const templateNode = fileItemTemplate.content.cloneNode(true); const itemElement = templateNode.querySelector('.file-item-container');
                    itemElement.dataset.id = newItemId; newItem.element = itemElement;
                    itemElement.querySelectorAll('input[type="radio"]').forEach(radio => { const originalName = radio.name; if (originalName.endsWith('_')) { radio.name = originalName + newItemId; } });
                    itemElement.querySelector('.file-name-display').textContent = formatFileName(file.name);
                    fileListContainer.appendChild(templateNode); fileItems.push(newItem);
                    console.log(`Item ${newItemId} added to UI and state. Triggering upload...`);
                    triggerUploadAndProcess(newItem);
                } catch (domError) { console.error(`Error creating UI for item ${newItemId}:`, domError); showGlobalStatus(`Error adding file ${file.name} to the list.`, 'error'); }
            });
            console.log('Resetting file input value.');
            event.target.value = null; updateFileCounter(); updateTotalPriceDisplay();
        }

        function handleOptionChange(event) {
            const target = event.target; if (target.type !== 'radio' || !target.dataset.option) return;
            const optionType = target.dataset.option; const itemElement = target.closest('.file-item-container'); if (!itemElement) return;
            const itemId = itemElement.dataset.id; const item = findItemById(itemId); if (!item || item.status !== 'processed' || target.disabled) return;
            if (optionType === 'printType') {
                const newType = parseInt(target.value, 10); if (item.printType !== newType) { item.printType = newType; console.log(`Item ${itemId}: Print type changed to ${item.printType === 1 ? 'Color' : 'B&W'}`); updateModalTitle(item); }
            } else if (optionType === 'pageRangeType') {
                const newRange = target.value; if (item.pageRangeType !== newRange) { const previousRange = item.pageRangeType; item.pageRangeType = newRange; console.log(`Item ${itemId}: Page range changed to ${item.pageRangeType}`); if (newRange === 'custom') { item.status = 'processed'; /* Don't re-process yet */ showItemStatus(itemElement, "Enter custom range and click Update.", "info"); const previewButton = itemElement.querySelector('.preview-icon'); if(previewButton) previewButton.disabled = true; updateCustomInputState(item); } else if (newRange === 'all' && previousRange === 'custom') { const needsServerUpdate = !item.fileInfo || item.startPage || item.endPage; if (needsServerUpdate) { console.log(`Item ${itemId}: Switching back to 'All Pages', re-processing on server...`); item.startPage = null; item.endPage = null; triggerUploadAndProcess(item, true); } else { console.log(`Item ${itemId}: Switching back to 'All Pages', already have full file info.`); hideItemStatus(itemElement); const previewButton = itemElement.querySelector('.preview-icon'); if(previewButton) previewButton.disabled = false; updateCustomInputState(item); } } }
            }
            highlightSelectedRadio(itemElement, optionType); updateItemUIState(item); updateTotalPriceDisplay(); // Update total price
        }

        function handleNumberInputChange(event) {
            const target = event.target; if (target.type !== 'number' || !target.dataset.option) return;
            const optionType = target.dataset.option; const itemElement = target.closest('.file-item-container'); if (!itemElement) return;
            const itemId = itemElement.dataset.id; const item = findItemById(itemId); if (!item || item.status !== 'processed') return;
            let value = parseInt(target.value, 10);
            if (optionType === 'numberOfCopies') {
                if (isNaN(value) || value < 1) value = 1; if (value > 99) value = 99; target.value = value;
                if (item.numberOfCopies !== value) { item.numberOfCopies = value; console.log(`Item ${itemId}: Copies changed to ${item.numberOfCopies}`); updateCopiesButtonStates(item); updateTotalPriceDisplay(); } // Update total
            } else if (optionType === 'startPage' || optionType === 'endPage') {
                const startInput = itemElement.querySelector('input[data-option="startPage"]'); const endInput = itemElement.querySelector('input[data-option="endPage"]'); const updateButton = itemElement.querySelector('.update-details-button');
                 const previewButton = itemElement.querySelector('.preview-icon');
                startInput.setCustomValidity(""); endInput.setCustomValidity(""); if (!startInput.checkValidity() || !endInput.checkValidity()) { updateButton.disabled = true; return; }
                const startVal = parseInt(startInput.value, 10); const endVal = parseInt(endInput.value, 10); const totalPages = item.fileInfo?.pageCount; let customError = false;
                if (!isNaN(startVal) && startVal < 1) { startInput.setCustomValidity(">= 1"); customError = true; }
                if (!isNaN(endVal) && !isNaN(startVal) && endVal < startVal) { endInput.setCustomValidity(">= Start"); customError = true; }
                if (!isNaN(endVal) && totalPages > 0 && endVal > totalPages) { endInput.setCustomValidity(`<= ${totalPages}`); customError = true; }
                const hasValues = startInput.value !== '' && endInput.value !== ''; updateButton.disabled = customError || !hasValues;
                if (!updateButton.disabled) { showItemStatus(itemElement, "Range changed. Click Update.", "info"); if(previewButton) previewButton.disabled = true; }
                else if (hasValues) { showItemStatus(itemElement, "Invalid page range.", "error"); if(previewButton) previewButton.disabled = true; }
                else { hideItemStatus(itemElement); /* Keep preview disabled if inputs empty */ if(previewButton) previewButton.disabled = true; }
                 // No price update here until 'Update' clicked
            }
        }

        function handleItemAction(event) {
            const button = event.target.closest('button[data-action]'); if (!button) return;
            const action = button.dataset.action; const itemElement = button.closest('.file-item-container'); if (!itemElement) return;
            const itemId = itemElement.dataset.id; const item = findItemById(itemId); if (!item) return;
            console.log(`Action '${action}' triggered for item ${itemId}`);
            switch (action) {
                case 'removeFile': removeItem(itemId); break;
                case 'previewFile':
                    console.log(`Preview action triggered for item:`, item.fileInfo ? JSON.stringify(item.fileInfo) : 'null');
                    if (item.status === 'processed' && item.fileInfo) { openPreviewModal(item); }
                    else { console.warn(`Preview clicked but item ${itemId} not processed or fileInfo missing.`); showItemStatus(itemElement, 'Preview unavailable yet.', 'info'); }
                    break;
                case 'decrementCopies': { const cInput = itemElement.querySelector('input[data-option="numberOfCopies"]'); let cVal = parseInt(cInput.value, 10); if (!isNaN(cVal) && cVal > 1) { cVal--; cInput.value = cVal; item.numberOfCopies = cVal; updateCopiesButtonStates(item); updateTotalPriceDisplay(); } break; } // Update total
                case 'incrementCopies': { const cInput = itemElement.querySelector('input[data-option="numberOfCopies"]'); let cVal = parseInt(cInput.value, 10); if (isNaN(cVal)) cVal = 0; if (cVal < 99) { cVal++; cInput.value = cVal; item.numberOfCopies = cVal; updateCopiesButtonStates(item); updateTotalPriceDisplay(); } break; } // Update total
                case 'updateDetails': if (item.pageRangeType === 'custom' && areCustomInputsValid(item)) { item.startPage = parseInt(itemElement.querySelector('input[data-option="startPage"]').value, 10); item.endPage = parseInt(itemElement.querySelector('input[data-option="endPage"]').value, 10); console.log(`Item ${itemId}: Updating with custom range ${item.startPage}-${item.endPage}`); triggerUploadAndProcess(item, true); button.disabled = true; button.textContent = "Updating..."; } else { showItemStatus(itemElement, "Invalid custom page range.", "error"); button.disabled = true; } break;
            }
        }

        function removeItem(itemId) {
            const itemIndex = fileItems.findIndex(item => item.id === itemId);
            if (itemIndex > -1) {
                const itemElement = fileItems[itemIndex].element;
                if (itemElement) {
                    itemElement.style.transition = 'opacity 0.3s ease-out, transform 0.3s ease-out';
                    itemElement.style.transform = 'scale(0.9)'; itemElement.style.opacity = '0';
                    setTimeout(() => { itemElement.remove(); updateFileCounter(); updateTotalPriceDisplay(); }, 250);
                }
                // Remove from state slightly later to ensure UI removal happens first
                 setTimeout(() => {
                     const idxToRemove = fileItems.findIndex(i => i.id === itemId);
                     if (idxToRemove > -1) {
                         fileItems.splice(idxToRemove, 1);
                         console.log(`Item ${itemId} removed from state.`);
                         updateTotalPriceDisplay(); // Update price after state removal
                         updateFileCounter(); // Update counter after state removal
                     }
                 }, 260);
                console.log(`Item ${itemId} removal initiated.`);
            }
        }

        // --- Core Upload/Processing ---
        function triggerUploadAndProcess(item, forceReprocess = false) {
            if (!item || !item.file) return;
            if (item.status !== 'new' && !forceReprocess) return;
            console.log(`Processing item ${item.id}: File=${item.file.name}, Range=${item.pageRangeType}, Start=${item.startPage}, End=${item.endPage}, Force=${forceReprocess}`);
            item.status = 'processing'; updateItemUIState(item);
            const formData = new FormData(); formData.append('file', item.file); formData.append('uniqueId', item.id); formData.append('pageRangeType', item.pageRangeType);
            if (item.pageRangeType === 'custom' && item.startPage && item.endPage) { formData.append('startPage', item.startPage); formData.append('endPage', item.endPage); }
            fetch('/print/upload', { method: 'POST', body: formData })
            .then(response => response.json().then(data => ({ ok: response.ok, status: response.status, data })))
            .then(({ ok, status, data }) => {
                console.log(`Response for item ${item.id} (Status ${status}):`, data);
                // Find item *again* inside the promise chain to ensure it wasn't removed
                 const currentItem = findItemById(item.id); // Use item.id, not data.uniqueId initially
                 if (!currentItem) {
                      console.warn(`Item ${item.id} was removed before processing completed. Ignoring response.`);
                      return; // Stop processing if item no longer exists
                  }
                  if (data.uniqueId !== currentItem.id) {
                      console.warn(`Received response for wrong uniqueId! Expected ${currentItem.id}, got ${data.uniqueId}. Ignoring.`);
                      return;
                  }

                if (!ok || data.status === 'error') { throw new Error(data.error || `Processing failed (HTTP ${status})`); }

                // Basic validation of expected fields
                if (data.status === 'processed' && data.pageCount >= 0 && data.c_url && data.fileName) {
                    currentItem.fileInfo = {
                        originalFileName: data.originalFileName,
                        fileName: data.fileName, // Assumed Color filename
                        bwFileName: data.bwFileName || null, // Store B&W filename if present
                        pageCount: data.pageCount,
                        mimeType: data.mimeType || currentItem.file.type,
                        url: data.url || null, // Store B&W URL if present
                        c_url: data.c_url // Assumed Color URL
                     };
                    currentItem.status = 'processed';
                    // Reset page range if type is 'all' or if reprocessed successfully
                    if (currentItem.pageRangeType === 'all') { currentItem.startPage = null; currentItem.endPage = null; }
                     // Reset update button state only if it was a 'custom' update
                    if (forceReprocess && currentItem.pageRangeType === 'custom') {
                         const updateBtn = currentItem.element?.querySelector('.update-details-button');
                         if (updateBtn) {
                             updateBtn.disabled = false; // Re-enable after successful update
                             updateBtn.textContent = "Update";
                         }
                     }
                     // Clear any info messages like "Click Update"
                     hideItemStatus(currentItem.element);
                     // Show info message only if B&W is selected but unavailable
                     if (currentItem.printType === 0 && !currentItem.fileInfo.url) {
                        showItemStatus(currentItem.element, "B&W version unavailable.", "info");
                     }
                     // Trigger UI update immediately
                     updateItemUIState(currentItem);

                } else {
                    // Handle cases where processing is 'complete' but essential data is missing
                    console.error("Processing response incomplete or invalid:", data);
                    throw new Error(data.error || "Processing response missing essential data.");
                 }
            }).catch(error => {
                console.error(`File Processing Error for item ${item.id}:`, error);
                 // Find item again inside catch
                 const currentItem = findItemById(item.id);
                 if (currentItem) {
                     currentItem.status = 'error';
                     currentItem.price = 0; // Reset price on error
                     showItemStatus(currentItem.element, `Error: ${error.message}`, 'error');
                      // Reset update button if it was updating
                     if (currentItem.pageRangeType === 'custom') {
                         const updateBtn = currentItem.element?.querySelector('.update-details-button');
                         if (updateBtn && updateBtn.textContent === 'Updating...') {
                              updateBtn.disabled = false; // Re-enable on error
                              updateBtn.textContent = "Update";
                          }
                     }
                     updateItemUIState(currentItem); // Update UI to reflect error state
                 }
            }).finally(() => {
                 // Find item one last time in finally
                 const currentItem = findItemById(item.id);
                 if (currentItem) {
                     console.log(`Processing finished for item ${currentItem.id}. Final status: ${currentItem.status}`);
                     // updateItemUIState is already called in .then and .catch
                 }
                 // Always update total price display after any processing attempt finishes
                 updateTotalPriceDisplay();
            });
        }

        // --- Payment Initiation ---
        function initiatePayment() {
            hideGlobalStatus();
            // Filter items that are *payable*: processed AND the *selected* print type is available
            const itemsToPay = fileItems.filter(item => {
                if (item.status !== 'processed' || !item.fileInfo || item.fileInfo.pageCount < 0) return false;
                const requiredUrl = (item.printType === 1) ? item.fileInfo.c_url : item.fileInfo.url;
                const requiredFileName = (item.printType === 1) ? item.fileInfo.fileName : item.fileInfo.bwFileName;
                return requiredUrl && requiredFileName; // Both must exist for the selected type
            });

            const finalTotalAmount = calculateAggregatedTotalPrice(itemsToPay); // Calculate based on filtered, payable items

            if (itemsToPay.length === 0) {
                 const hasPendingOrError = fileItems.some(i => i.status !== 'processed');
                 const hasUnavailableSelection = fileItems.some(item => {
                      if (item.status !== 'processed' || !item.fileInfo) return false;
                      const requiredUrl = (item.printType === 1) ? item.fileInfo.c_url : item.fileInfo.url;
                      const requiredFileName = (item.printType === 1) ? item.fileInfo.fileName : item.fileInfo.bwFileName;
                      return !requiredUrl || !requiredFileName;
                  });

                 if (hasPendingOrError) {
                     showGlobalStatus('Some files are still processing or have errors.', 'warning');
                 } else if (hasUnavailableSelection) {
                      showGlobalStatus('Some selected print types are unavailable.', 'warning');
                 } else if (fileItems.length > 0 && fileItems.every(i => i.status === 'processed' && i.fileInfo?.pageCount === 0)) {
                     // Special case: Only zero-page files processed, allow "payment" (which should be skipped backend)
                      console.log("Attempting payment for only zero-page files.");
                      // Proceed to payload creation below
                 }
                 else {
                     showGlobalStatus('No valid items ready for payment.', 'warning');
                 }
                 // Only return if not the zero-page special case
                 if (!(itemsToPay.length === 0 && fileItems.length > 0 && fileItems.every(i => i.status === 'processed' && i.fileInfo?.pageCount === 0 && !hasPendingOrError && !hasUnavailableSelection))) {
                    return;
                 }
            }

            // Handle the zero-amount case (either actual zero pages or only zero-page items)
             if (finalTotalAmount <= 0 && !itemsToPay.every(i => i.fileInfo?.pageCount === 0)) {
                 // This case means price is zero but there are non-zero page items (shouldn't happen with current pricing)
                 showGlobalStatus('Cannot proceed with zero amount for items with pages.', 'error');
                 console.error("Payment attempt with zero amount but non-zero pages detected.");
                 return;
             }

            payButton.disabled = true; showLoader(paymentLoader); showGlobalStatus('Initiating payment...', 'info');
            // Payload still contains individual item details for webhook processing
            // Ensure fileName in payload matches the *selected* print type
            const payloadItems = itemsToPay.map(item => ({
                 uniqueId: item.id,
                 fileName: (item.printType === 1) ? item.fileInfo.fileName : item.fileInfo.bwFileName,
                 pageCount: item.fileInfo.pageCount,
                 printType: item.printType, // Send selected print type
                 numberOfCopies: item.numberOfCopies,
                 printerId: printerId
            }));
            const payload = { items: payloadItems }; console.log('Initiating payment with payload:', JSON.stringify(payload));
            fetch('/print/api/payments/initiate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
            .then(response => response.json().then(data => ({ ok: response.ok, status: response.status, data })))
            .then(({ ok, status, data }) => {
                console.log('Payment Initiation Response:', data); if (!ok) { throw new Error(data.error || `Payment initiation failed (HTTP ${status})`); }
                if (data.status === 'skipped_zero_amount') {
                    console.log("Payment skipped by server (0 amount). Simulating success.");
                    showGlobalStatus("Order processed (0 amount). Files are being sent.", 'success');
                    hideLoader(paymentLoader);
                    // Clear successful items immediately
                    fileItems.forEach(item => item.element?.remove());
                    fileItems = [];
                    updateFileCounter();
                    updateTotalPriceDisplay(); // Should show "Add files..."
                    payButton.disabled = true; // Keep disabled after successful order
                    return;
                 }
                if (!data.razorpayKey || !data.orderId || typeof data.amount !== 'number') { throw new Error("Invalid order data received from server."); }
                const description = `Print Order: ${itemsToPay.length} file(s) - Total Rs. ${data.amount / 100}`; // Use amount from server response for description consistency
                openRazorpayCheckout(data, description);
            }).catch(error => {
                console.error('Payment Initiation Error:', error); showGlobalStatus(`Error: ${error.message}`, 'error');
                // Re-enable pay button on initiation error
                 payButton.disabled = false; // Or recalculate disabled state via updateTotalPriceDisplay()
                 updateTotalPriceDisplay(); // Recalculate/update button state
                 hideLoader(paymentLoader);
            });
        }
        // testing the timer 
        let paymentTimerIntervalId = null;

// --- Helper function to clear the timer ---
function clearPaymentTimer() {
    if (paymentTimerIntervalId) {
        clearInterval(paymentTimerIntervalId);
        paymentTimerIntervalId = null;
        // Optionally find and remove the timer span if it exists
        const timerDisplay = document.getElementById('payment-timer-display');
        if (timerDisplay) {
            timerDisplay.textContent = ''; // Clear text or remove element
        }
    }
}


function openRazorpayCheckout(orderData, description) {
    console.log("Opening Razorpay. Amount:", orderData.amount, "Description:", description);

    // --- Ensure any previous timer is cleared before starting checkout ---
    // (Optional, but good practice if checkout can be re-initiated)
    // clearPaymentTimer();

    if (typeof Razorpay === 'undefined') {
        console.error("Razorpay checkout.js script not loaded.");
        showGlobalStatus("Error: Payment gateway script failed to load.", 'error');
        hideLoader(paymentLoader);
        updateTotalPriceDisplay();
        return;
    }

    const options = {
        key: orderData.razorpayKey,
        amount: orderData.amount,
        currency: orderData.currency || "INR",
        name: "KsDigitalServices",
        description: description,
        order_id: orderData.orderId,
        handler: function (response) {
            console.log("Razorpay Success:", response);

            // --- Timer Logic Start ---
            clearPaymentTimer(); // Clear any lingering timer first
            let timeLeft = 30; // Start countdown from 30 seconds

            // Assume globalStatusMessage is the element showGlobalStatus updates
            // Manually set the success message with the timer span
            if (globalStatusMessage) {
                globalStatusMessage.innerHTML = `Payment Successful! Files are being sent. <span id="payment-timer-display" style="font-weight:bold; margin-left: 5px;">${timeLeft}s</span>`;
                globalStatusMessage.className = 'status-message status-success'; // Set success style
                 globalStatusMessage.style.display = 'block'; // Ensure visible
            } else {
                // Fallback if the element isn't found
                 console.log(`Payment Successful! Files are being sent. (${timeLeft}s)`);
            }


            const timerDisplay = document.getElementById('payment-timer-display');

            paymentTimerIntervalId = setInterval(() => {
                timeLeft--;
                if (timerDisplay) {
                    timerDisplay.textContent = `${timeLeft}s`;
                }

                if (timeLeft <= 0) {
                    clearPaymentTimer(); // Stop the interval
                    if (timerDisplay) {
                        timerDisplay.textContent = ''; // Clear the timer text
                        // Optionally update the main message again now timer is done
                         if (globalStatusMessage) {
                           globalStatusMessage.innerHTML = 'Payment Successful! Files sent.';
                         }
                    }
                }
            }, 1000); // Update every second
            // --- Timer Logic End ---

            hideLoader(paymentLoader);

            // Clear successful items immediately
            fileItems.forEach(item => item.element?.remove());
            fileItems = [];
            updateFileCounter();
            updateTotalPriceDisplay(); // Should show "Add files..." / 0 price
            payButton.disabled = true; // Keep disabled after successful order
        },
        prefill: {}, // Add prefill details if available
        notes: { order_timestamp: new Date().toISOString() },
        theme: { color: getComputedStyle(document.documentElement).getPropertyValue('--primary-color').trim() || "#007bff" },
        modal: {
            ondismiss: function() {
                console.log('Razorpay checkout closed.');
                clearPaymentTimer(); // Stop timer if modal is closed
                // Only show 'cancelled' if payment didn't succeed before closing
                // Check the class directly on the assumed status element
                 if (!globalStatusMessage || !globalStatusMessage.classList.contains('status-success')) {
                    showGlobalStatus('Payment cancelled or closed.', 'info');
                    updateTotalPriceDisplay(); // Re-enable button maybe? Check logic
                }
                hideLoader(paymentLoader);
            }
        }
    };

    try {
        const rzp = new Razorpay(options);
        rzp.on('payment.failed', function (response) {
            console.error("Razorpay Payment Failed:", response.error);
            clearPaymentTimer(); // Stop timer on failure
            let errorMsg = `Payment Failed: ${response.error.description || 'Unknown error'}`;
            if (response.error.reason) errorMsg += ` (Reason: ${response.error.reason})`;
            showGlobalStatus(errorMsg, 'error');
            updateTotalPriceDisplay(); // Re-enable button if applicable
            hideLoader(paymentLoader);
        });
        rzp.open();
    } catch (e) {
        console.error("Error initializing Razorpay:", e);
        clearPaymentTimer(); // Ensure timer cleared on init error too
        showGlobalStatus("Could not initialize payment gateway.", 'error');
        updateTotalPriceDisplay(); // Re-enable button if applicable
        hideLoader(paymentLoader);
    }
}

        // --- Modal & Preview Rendering ---
        function openPreviewModal(item) {
            console.log(`Attempting to open preview modal for item ${item.id}. Status: ${item.status}`);
            console.log(`File Info:`, item.fileInfo ? JSON.stringify(item.fileInfo) : 'null'); console.log(`Selected printType: ${item.printType}`);
            if (!item || item.status !== 'processed' || !item.fileInfo) { modalPreviewTitle.textContent = "Preview Unavailable"; modalPreviewContent.innerHTML = '<p class="placeholder">File processing failed or is not complete.</p>'; previewModal.classList.add('visible'); return; }

            // Determine URL and filename based on the SELECTED print type
            let urlToDisplay = null;
            let fileNameForTitle = item.fileInfo.originalFileName || 'Unknown File'; // Use original for title generally
            let fileType = item.fileInfo.mimeType; // MimeType is same for both versions

            if (item.printType === 1) { // Color selected
                urlToDisplay = item.fileInfo.c_url;
                if (!urlToDisplay || !item.fileInfo.fileName) {
                     console.error("Preview unavailable: Color version missing URL or filename.");
                     modalPreviewTitle.textContent = "Preview Unavailable (Color)";
                     modalPreviewContent.innerHTML = '<p class="placeholder">The Color version preview is not available for this file.</p>';
                     previewModal.classList.add('visible');
                     return;
                }
            } else { // B&W selected
                urlToDisplay = item.fileInfo.url;
                 if (!urlToDisplay || !item.fileInfo.bwFileName) {
                     console.error("Preview unavailable: B&W version missing URL or filename.");
                     modalPreviewTitle.textContent = "Preview Unavailable (B&W)";
                     modalPreviewContent.innerHTML = '<p class="placeholder">The B&W version preview is not available for this file.</p>';
                     previewModal.classList.add('visible');
                     return;
                 }
            }

            console.log(`Using preview URL: ${urlToDisplay} (Type: ${fileType})`);
            updateModalTitle(item); // Update title with correct context
            modalPreviewContent.innerHTML = '<p class="placeholder">Loading preview...</p>';
            previewModal.classList.add('visible');

            renderPreviewInModal(urlToDisplay, fileType, item.fileInfo.pageCount);
        }
        function closePreviewModalHandler() { previewModal.classList.remove('visible'); modalPreviewContent.innerHTML = ''; }
        function updateModalTitle(item = null) {
            if (!item || !item.fileInfo) { modalPreviewTitle.textContent = "File Preview"; return; }
            const typeText = item.printType === 1 ? "Color" : "B&W";
            const nameText = formatFileName(item.fileInfo.originalFileName || 'Unknown File', 25); // Use original name
            let detailsText = '';
            if (item.fileInfo.mimeType?.startsWith('image/')) { detailsText = "(Image)"; }
            else {
                 // Page count shown should reflect the *processed* file's total pages, not custom range
                 const pageCount = item.fileInfo.pageCount;
                 const pageText = pageCount > 1 ? `${pageCount} Pgs` : (pageCount === 1 ? `1 Pg` : '0 Pgs');
                 // Indicate if a custom range *was* applied for processing (even if preview shows all)
                 const rangeInfo = (item.startPage && item.endPage) ? `(Processed: ${item.startPage}-${item.endPage}, ${pageText})` : `(${pageText})`;
                 detailsText = rangeInfo;
            }
            modalPreviewTitle.textContent = `Preview: ${nameText} - ${typeText} ${detailsText}`;
        }
        async function renderPreviewInModal(url, mimeType, pageCount) {
            if (!url || !mimeType) { modalPreviewContent.innerHTML = '<p class="placeholder">Preview data is missing.</p>'; return; }
            modalPreviewContent.innerHTML = ''; // Clear previous content
            const loadingIndicator = document.createElement('div'); loadingIndicator.className = 'loader'; loadingIndicator.style.display = 'block'; loadingIndicator.style.margin = '20px auto'; modalPreviewContent.appendChild(loadingIndicator);
            try {
                if (mimeType.startsWith('image/')) {
                    const img = document.createElement('img'); img.className = 'pdf-page-canvas'; img.style.boxShadow = 'none'; img.alt = `Preview`;
                    img.onload = () => hideLoader(loadingIndicator);
                    img.onerror = () => { console.error(`Error loading image preview from URL: ${url}`); modalPreviewContent.innerHTML = '<p class="placeholder">Error loading image preview.</p>'; hideLoader(loadingIndicator); };
                    img.src = url; modalPreviewContent.appendChild(img);
                    // hideLoader(loadingIndicator); // Already handled in onload
                } else if (mimeType === 'application/pdf') {
                    if (typeof pdfjsLib === 'undefined' || !pdfjsLib.getDocument) { throw new Error("PDF.js library failed to load properly."); }
                    await renderPdfPreview(url, modalPreviewContent, pageCount); // Will handle its own loader removal
                } else { throw new Error(`Preview not available for this file type (${mimeType}).`); }
            } catch (err) {
                 console.error("Error creating preview element:", err);
                 modalPreviewContent.innerHTML = `<p class="placeholder">Error displaying preview: ${err.message}</p>`;
                 hideLoader(loadingIndicator); // Ensure loader is hidden on error
            }
        }
        async function renderPdfPreview(pdfUrl, containerElement, expectedPageCount) {
            if (typeof pdfjsLib === 'undefined' || !pdfjsLib.getDocument) throw new Error("PDF.js library not available.");
            console.log(`renderPdfPreview: URL=${pdfUrl}`);

            let loader = containerElement.querySelector('.loader');
            if (!loader) {
                loader = document.createElement('div');
                loader.className = 'loader';
                containerElement.innerHTML = '';
                containerElement.appendChild(loader);
            }
            loader.style.display = 'block';

            let loadingTask = null;
            try {
                loadingTask = pdfjsLib.getDocument({ url: pdfUrl });
                const pdf = await loadingTask.promise;
                if (!pdf || pdf.numPages === 0) throw new Error("PDF is invalid or has no pages.");
                console.log(`PDF loaded successfully: ${pdf.numPages} pages.`);

                containerElement.innerHTML = ''; // Remove loader

                const pagesContainer = document.createElement('div');
                containerElement.appendChild(pagesContainer);

                await new Promise(resolve => requestAnimationFrame(resolve)); // Allow reflow

                const containerPadding = parseFloat(window.getComputedStyle(containerElement).paddingLeft || '0') + parseFloat(window.getComputedStyle(containerElement).paddingRight || '0');
                const availableWidth = Math.max(containerElement.clientWidth - containerPadding, 150);
                console.log(`Available display width for PDF rendering: ${availableWidth}px`);

                // --- *** HIGH QUALITY RENDERING SETUP *** ---
                // Get the device pixel ratio, defaulting to 1 if not available
                const devicePixelRatio = window.devicePixelRatio || 1;
                console.log(`Device Pixel Ratio: ${devicePixelRatio}`);
                // --- *** END HIGH QUALITY SETUP *** ---


                // --- SEQUENTIAL RENDERING LOOP ---
                for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
                    console.log(`Processing page ${pageNum}...`);
                    let page = null;
                    try {
                        page = await pdf.getPage(pageNum);
                        const viewport = page.getViewport({ scale: 1 }); // Get viewport at scale 1

                        // --- Calculate Scales ---
                        // 1. Base scale to fit the available width
                        const baseScale = availableWidth / viewport.width;
                        // 2. Rendering scale adjusted for device pixel ratio
                        const renderScale = baseScale * devicePixelRatio;
                        // --- End Calculate Scales ---

                        // Get viewport at the higher rendering scale
                        const renderViewport = page.getViewport({ scale: renderScale });

                        const canvas = document.createElement('canvas');
                        canvas.className = 'pdf-page-canvas';
                        const context = canvas.getContext('2d');

                        // --- Set Canvas Dimensions ---
                        // Set the actual canvas buffer size to the higher resolution
                        canvas.height = Math.round(renderViewport.height);
                        canvas.width = Math.round(renderViewport.width);

                        // Set the CSS display size to the desired size (based on baseScale)
                        canvas.style.width = `${Math.round(viewport.width * baseScale)}px`; // or just `${availableWidth}px`
                        canvas.style.height = `${Math.round(viewport.height * baseScale)}px`;
                        // --- End Set Canvas Dimensions ---


                        console.log(`Page ${pageNum}: Render Scale=${renderScale.toFixed(2)}, Canvas=${canvas.width}x${canvas.height}, CSS=${canvas.style.width}x${canvas.style.height}`);

                        pagesContainer.appendChild(canvas); // Append canvas BEFORE rendering

                        const renderContext = {
                            canvasContext: context,
                            viewport: renderViewport
                        };

                        console.log(`Rendering page ${pageNum}...`);
                        await page.render(renderContext).promise;
                        console.log(`Rendered page ${pageNum}`);

                    } catch (renderError) {
                        console.error(`Error rendering page ${pageNum}:`, renderError);
                        const errorPlaceholder = document.createElement('div');
                        errorPlaceholder.textContent = `Error rendering page ${pageNum}.`;
                        errorPlaceholder.style.cssText = `color: red; border: 1px dashed red; padding: 10px; margin: 10px auto; max-width: ${availableWidth}px;`;
                        pagesContainer.appendChild(errorPlaceholder);
                    } finally {
                        if (page) {
                            page.cleanup();
                            console.log(`Cleaned up page ${pageNum}`);
                        }
                    }
                    // Optional delay (uncomment if needed)
                    // await new Promise(resolve => setTimeout(resolve, 10));
                }
                // --- END SEQUENTIAL LOOP ---

                console.log('All PDF pages processed sequentially for high quality.');

            } catch (error) {
                 console.error(`renderPdfPreview Error: ${error.name}`, error);
                 containerElement.innerHTML = `<p class="placeholder">Error loading PDF preview: ${error.message || error.name}</p>`;
            } finally {
                 if (loadingTask && typeof loadingTask.destroy === 'function') {
                    try { await loadingTask.destroy(); console.log("PDF loading task destroyed."); }
                    catch (destroyError) { console.warn("Error destroying PDF loading task:", destroyError); }
                 }
                 const finalLoader = containerElement.querySelector('.loader');
                 if (finalLoader) finalLoader.remove();
            }
        }


        // --- Run Initialization ---
        initializeApp();

    });