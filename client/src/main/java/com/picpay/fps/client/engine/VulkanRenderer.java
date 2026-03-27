package com.picpay.fps.client.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Vulkan renderer for the FPS client.
 * Handles instance, device, swapchain, pipeline, and rendering.
 *
 * Renders colored geometry (position + color per vertex) with push constants for MVP matrix.
 */
public class VulkanRenderer {
    private static final Logger LOG = Logger.getLogger(VulkanRenderer.class.getName());
    private static final int MAX_FRAMES_IN_FLIGHT = 2;

    private Window window;

    // Vulkan core
    private VkInstance instance;
    private long surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue graphicsQueue;
    private VkQueue presentQueue;
    private int graphicsFamily, presentFamily;

    // Swapchain
    private long swapchain;
    private List<Long> swapchainImages;
    private int swapchainFormat;
    private VkExtent2D swapchainExtent;
    private List<Long> swapchainImageViews;

    // Render pass & pipeline
    private long renderPass;
    private long pipelineLayout;
    private long graphicsPipeline;
    private long descriptorSetLayout;

    // Framebuffers
    private List<Long> framebuffers;

    // Command pool & buffers
    private long commandPool;
    private List<VkCommandBuffer> commandBuffers;

    // Sync
    private List<Long> imageAvailableSemaphores;
    private List<Long> renderFinishedSemaphores;
    private List<Long> inFlightFences;
    private int currentFrame = 0;

    // Depth buffer
    private long depthImage;
    private long depthImageMemory;
    private long depthImageView;

    // Vertex buffer (dynamic — rebuilt each frame)
    private long vertexBuffer;
    private long vertexBufferMemory;
    private int vertexCount;
    private static final int MAX_VERTICES = 100_000;
    private static final int VERTEX_SIZE = 6 * 4; // pos(3f) + color(3f) = 24 bytes

    public void init(Window window) {
        this.window = window;
        createInstance();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
        createSwapchain();
        createImageViews();
        createDepthResources();
        createRenderPass();
        createGraphicsPipeline();
        createFramebuffers();
        createCommandPool();
        createVertexBuffer();
        createCommandBuffers();
        createSyncObjects();
        LOG.info("Vulkan initialized successfully");
    }

    private void createInstance() {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType$Default()
                .pApplicationName(stack.UTF8("FPS Intranet"))
                .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
                .pEngineName(stack.UTF8("PicPay Engine"))
                .engineVersion(VK_MAKE_VERSION(0, 1, 0))
                .apiVersion(VK_API_VERSION_1_0);

            // Required extensions for GLFW
            PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) throw new RuntimeException("GLFW Vulkan not supported");

            // Check available extensions to see if portability enumeration is supported
            IntBuffer extCount = stack.mallocInt(1);
            vkEnumerateInstanceExtensionProperties((CharSequence) null, extCount, null);
            VkExtensionProperties.Buffer availableExts = VkExtensionProperties.calloc(extCount.get(0), stack);
            vkEnumerateInstanceExtensionProperties((CharSequence) null, extCount, availableExts);

            boolean hasPortabilityEnum = false;
            for (int i = 0; i < extCount.get(0); i++) {
                if (availableExts.get(i).extensionNameString().equals("VK_KHR_portability_enumeration")) {
                    hasPortabilityEnum = true;
                    break;
                }
            }

            // Build extension list: GLFW required + portability if available
            int extCountTotal = glfwExtensions.capacity() + (hasPortabilityEnum ? 1 : 0);
            PointerBuffer extensions = stack.mallocPointer(extCountTotal);
            extensions.put(glfwExtensions);
            if (hasPortabilityEnum) {
                extensions.put(stack.UTF8("VK_KHR_portability_enumeration"));
            }
            extensions.flip();

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType$Default()
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(extensions)
                .ppEnabledLayerNames(null);

            if (hasPortabilityEnum) {
                createInfo.flags(0x00000001); // VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
            }

            PointerBuffer pInstance = stack.mallocPointer(1);
            int result = vkCreateInstance(createInfo, null, pInstance);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create Vulkan instance: " + result);

            instance = new VkInstance(pInstance.get(0), createInfo);
            LOG.info("Vulkan instance created (portability=" + hasPortabilityEnum + ")");
        }
    }

    private void createSurface() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            int result = glfwCreateWindowSurface(instance, window.getHandle(), null, pSurface);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create window surface");
            surface = pSurface.get(0);
        }
    }

    private void pickPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            vkEnumeratePhysicalDevices(instance, count, null);
            if (count.get(0) == 0) throw new RuntimeException("No Vulkan GPU found");

            PointerBuffer devices = stack.mallocPointer(count.get(0));
            vkEnumeratePhysicalDevices(instance, count, devices);

            // Pick first suitable device
            for (int i = 0; i < count.get(0); i++) {
                VkPhysicalDevice dev = new VkPhysicalDevice(devices.get(i), instance);
                if (isDeviceSuitable(dev, stack)) {
                    physicalDevice = dev;
                    VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
                    vkGetPhysicalDeviceProperties(dev, props);
                    LOG.info("GPU: " + props.deviceNameString());
                    return;
                }
            }
            throw new RuntimeException("No suitable GPU found");
        }
    }

    private boolean isDeviceSuitable(VkPhysicalDevice device, MemoryStack stack) {
        findQueueFamilies(device, stack);
        return graphicsFamily >= 0 && presentFamily >= 0;
    }

    private void findQueueFamilies(VkPhysicalDevice device, MemoryStack stack) {
        graphicsFamily = -1;
        presentFamily = -1;

        IntBuffer count = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(device, count, null);
        VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(device, count, families);

        IntBuffer presentSupport = stack.mallocInt(1);
        for (int i = 0; i < count.get(0); i++) {
            if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                graphicsFamily = i;
            }
            vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport);
            if (presentSupport.get(0) == VK_TRUE) {
                presentFamily = i;
            }
            if (graphicsFamily >= 0 && presentFamily >= 0) break;
        }
    }

    private void createLogicalDevice() {
        try (MemoryStack stack = stackPush()) {
            findQueueFamilies(physicalDevice, stack);

            // Queue create infos
            int[] uniqueFamilies = graphicsFamily == presentFamily
                ? new int[]{graphicsFamily}
                : new int[]{graphicsFamily, presentFamily};

            VkDeviceQueueCreateInfo.Buffer queueInfos = VkDeviceQueueCreateInfo.calloc(uniqueFamilies.length, stack);
            for (int i = 0; i < uniqueFamilies.length; i++) {
                queueInfos.get(i)
                    .sType$Default()
                    .queueFamilyIndex(uniqueFamilies[i])
                    .pQueuePriorities(stack.floats(1.0f));
            }

            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);

            // Extensions — check what's available
            IntBuffer devExtCount = stack.mallocInt(1);
            vkEnumerateDeviceExtensionProperties(physicalDevice, (CharSequence) null, devExtCount, null);
            VkExtensionProperties.Buffer devExts = VkExtensionProperties.calloc(devExtCount.get(0), stack);
            vkEnumerateDeviceExtensionProperties(physicalDevice, (CharSequence) null, devExtCount, devExts);

            boolean hasPortabilitySubset = false;
            for (int i = 0; i < devExtCount.get(0); i++) {
                if (devExts.get(i).extensionNameString().equals("VK_KHR_portability_subset")) {
                    hasPortabilitySubset = true;
                    break;
                }
            }

            List<String> deviceExtensions = new ArrayList<>();
            deviceExtensions.add(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
            if (hasPortabilitySubset) {
                deviceExtensions.add("VK_KHR_portability_subset");
            }

            PointerBuffer extBuf = stack.mallocPointer(deviceExtensions.size());
            for (String ext : deviceExtensions) {
                extBuf.put(stack.UTF8(ext));
            }
            extBuf.flip();

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                .sType$Default()
                .pQueueCreateInfos(queueInfos)
                .pEnabledFeatures(features)
                .ppEnabledExtensionNames(extBuf);

            PointerBuffer pDevice = stack.mallocPointer(1);
            int result = vkCreateDevice(physicalDevice, createInfo, null, pDevice);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create logical device: " + result);

            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, graphicsFamily, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), device);

            vkGetDeviceQueue(device, presentFamily, 0, pQueue);
            presentQueue = new VkQueue(pQueue.get(0), device);
        }
    }

    private void createSwapchain() {
        try (MemoryStack stack = stackPush()) {
            VkSurfaceCapabilitiesKHR caps = VkSurfaceCapabilitiesKHR.calloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, caps);

            // Pick format
            IntBuffer formatCount = stack.mallocInt(1);
            vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null);
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(formatCount.get(0), stack);
            vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, formats);

            VkSurfaceFormatKHR chosen = formats.get(0);
            for (int i = 0; i < formatCount.get(0); i++) {
                if (formats.get(i).format() == VK_FORMAT_B8G8R8A8_SRGB
                    && formats.get(i).colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    chosen = formats.get(i);
                    break;
                }
            }
            swapchainFormat = chosen.format();

            // Pick extent
            if (caps.currentExtent().width() != -1) {
                swapchainExtent = VkExtent2D.calloc().set(caps.currentExtent());
            } else {
                swapchainExtent = VkExtent2D.calloc()
                    .width(Math.clamp(window.getWidth(), caps.minImageExtent().width(), caps.maxImageExtent().width()))
                    .height(Math.clamp(window.getHeight(), caps.minImageExtent().height(), caps.maxImageExtent().height()));
            }

            int imageCount = caps.minImageCount() + 1;
            if (caps.maxImageCount() > 0) imageCount = Math.min(imageCount, caps.maxImageCount());

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType$Default()
                .surface(surface)
                .minImageCount(imageCount)
                .imageFormat(swapchainFormat)
                .imageColorSpace(chosen.colorSpace())
                .imageExtent(swapchainExtent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .preTransform(caps.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(VK_PRESENT_MODE_FIFO_KHR)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE);

            if (graphicsFamily != presentFamily) {
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                    .pQueueFamilyIndices(stack.ints(graphicsFamily, presentFamily));
            } else {
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            LongBuffer pSwapchain = stack.mallocLong(1);
            int result = vkCreateSwapchainKHR(device, createInfo, null, pSwapchain);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create swapchain: " + result);
            swapchain = pSwapchain.get(0);

            // Get images
            IntBuffer imgCount = stack.mallocInt(1);
            vkGetSwapchainImagesKHR(device, swapchain, imgCount, null);
            LongBuffer images = stack.mallocLong(imgCount.get(0));
            vkGetSwapchainImagesKHR(device, swapchain, imgCount, images);

            swapchainImages = new ArrayList<>();
            for (int i = 0; i < imgCount.get(0); i++) {
                swapchainImages.add(images.get(i));
            }
        }
    }

    private void createImageViews() {
        swapchainImageViews = new ArrayList<>();
        for (long image : swapchainImages) {
            swapchainImageViews.add(createImageView(image, swapchainFormat, VK_IMAGE_ASPECT_COLOR_BIT));
        }
    }

    private long createImageView(long image, int format, int aspectFlags) {
        try (MemoryStack stack = stackPush()) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType$Default()
                .image(image)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(format)
                .subresourceRange(r -> r
                    .aspectMask(aspectFlags)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1));

            LongBuffer pView = stack.mallocLong(1);
            vkCreateImageView(device, viewInfo, null, pView);
            return pView.get(0);
        }
    }

    private int findMemoryType(int typeFilter, int properties) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProps);

            for (int i = 0; i < memProps.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0
                    && (memProps.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }
            throw new RuntimeException("Failed to find suitable memory type");
        }
    }

    private void createDepthResources() {
        int depthFormat = VK_FORMAT_D32_SFLOAT;

        try (MemoryStack stack = stackPush()) {
            // Create depth image
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                .sType$Default()
                .imageType(VK_IMAGE_TYPE_2D)
                .extent(e -> e.width(swapchainExtent.width()).height(swapchainExtent.height()).depth(1))
                .mipLevels(1)
                .arrayLayers(1)
                .format(depthFormat)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .samples(VK_SAMPLE_COUNT_1_BIT);

            LongBuffer pImage = stack.mallocLong(1);
            vkCreateImage(device, imageInfo, null, pImage);
            depthImage = pImage.get(0);

            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, depthImage, memReqs);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .allocationSize(memReqs.size())
                .memoryTypeIndex(findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            LongBuffer pMemory = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMemory);
            depthImageMemory = pMemory.get(0);

            vkBindImageMemory(device, depthImage, depthImageMemory, 0);

            depthImageView = createImageView(depthImage, depthFormat, VK_IMAGE_ASPECT_DEPTH_BIT);
        }
    }

    private void createRenderPass() {
        try (MemoryStack stack = stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);

            // Color attachment
            attachments.get(0)
                .format(swapchainFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            // Depth attachment
            attachments.get(1)
                .format(VK_FORMAT_D32_SFLOAT)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkAttachmentReference depthRef = VkAttachmentReference.calloc(stack)
                .attachment(1)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorRef)
                .pDepthStencilAttachment(depthRef);

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                .srcAccessMask(0)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType$Default()
                .pAttachments(attachments)
                .pSubpasses(subpass)
                .pDependencies(dependency);

            LongBuffer pRenderPass = stack.mallocLong(1);
            vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
            renderPass = pRenderPass.get(0);
        }
    }

    private void createGraphicsPipeline() {
        try (MemoryStack stack = stackPush()) {
            // Compile shaders using shaderc at runtime
            long vertModule = createShaderModule(compileShader(VERTEX_SHADER_SRC, org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader));
            long fragModule = createShaderModule(compileShader(FRAGMENT_SHADER_SRC, org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader));

            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0)
                .sType$Default()
                .stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertModule)
                .pName(stack.UTF8("main"));
            shaderStages.get(1)
                .sType$Default()
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragModule)
                .pName(stack.UTF8("main"));

            // Vertex input: position(3f) + color(3f)
            VkVertexInputBindingDescription.Buffer bindingDesc = VkVertexInputBindingDescription.calloc(1, stack)
                .binding(0)
                .stride(VERTEX_SIZE)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkVertexInputAttributeDescription.Buffer attrDesc = VkVertexInputAttributeDescription.calloc(2, stack);
            attrDesc.get(0).binding(0).location(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);         // position
            attrDesc.get(1).binding(0).location(1).format(VK_FORMAT_R32G32B32_SFLOAT).offset(3 * 4);     // color

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType$Default()
                .pVertexBindingDescriptions(bindingDesc)
                .pVertexAttributeDescriptions(attrDesc);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType$Default()
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                .x(0).y(0)
                .width(swapchainExtent.width())
                .height(swapchainExtent.height())
                .minDepth(0).maxDepth(1);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack)
                .offset(o -> o.x(0).y(0))
                .extent(swapchainExtent);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType$Default()
                .pViewports(viewport)
                .pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType$Default()
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .lineWidth(1.0f)
                .cullMode(VK_CULL_MODE_BACK_BIT)
                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType$Default()
                .sampleShadingEnable(false)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType$Default()
                .depthTestEnable(true)
                .depthWriteEnable(true)
                .depthCompareOp(VK_COMPARE_OP_LESS)
                .depthBoundsTestEnable(false)
                .stencilTestEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer blendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                .blendEnable(false);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType$Default()
                .logicOpEnable(false)
                .pAttachments(blendAttachment);

            // Push constants: MVP matrix (64 bytes = 4x4 floats)
            VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                .offset(0)
                .size(64);

            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pPushConstantRanges(pushConstants);

            LongBuffer pLayout = stack.mallocLong(1);
            vkCreatePipelineLayout(device, layoutInfo, null, pLayout);
            pipelineLayout = pLayout.get(0);

            // Dynamic state for viewport/scissor (for resize)
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType$Default()
                .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType$Default()
                .pStages(shaderStages)
                .pVertexInputState(vertexInput)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizer)
                .pMultisampleState(multisampling)
                .pDepthStencilState(depthStencil)
                .pColorBlendState(colorBlending)
                .pDynamicState(dynamicState)
                .layout(pipelineLayout)
                .renderPass(renderPass)
                .subpass(0);

            LongBuffer pPipeline = stack.mallocLong(1);
            vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            graphicsPipeline = pPipeline.get(0);

            vkDestroyShaderModule(device, vertModule, null);
            vkDestroyShaderModule(device, fragModule, null);
        }
    }

    private ByteBuffer compileShader(String source, int shaderKind) {
        long compiler = org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize();
        long result = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv(
            compiler, source, shaderKind, "shader", "main", 0L);

        if (org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status(result)
            != org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success) {
            String error = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message(result);
            throw new RuntimeException("Shader compilation failed: " + error);
        }

        ByteBuffer spirv = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes(result);
        // Copy to managed buffer since result will be released
        ByteBuffer copy = MemoryUtil.memAlloc(spirv.remaining());
        copy.put(spirv);
        copy.flip();

        org.lwjgl.util.shaderc.Shaderc.shaderc_result_release(result);
        org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release(compiler);

        return copy;
    }

    private long createShaderModule(ByteBuffer code) {
        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType$Default()
                .pCode(code);

            LongBuffer pModule = stack.mallocLong(1);
            vkCreateShaderModule(device, createInfo, null, pModule);
            MemoryUtil.memFree(code);
            return pModule.get(0);
        }
    }

    private void createFramebuffers() {
        framebuffers = new ArrayList<>();
        for (long imageView : swapchainImageViews) {
            try (MemoryStack stack = stackPush()) {
                LongBuffer attachments = stack.longs(imageView, depthImageView);

                VkFramebufferCreateInfo fbInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .pAttachments(attachments)
                    .width(swapchainExtent.width())
                    .height(swapchainExtent.height())
                    .layers(1);

                LongBuffer pFb = stack.mallocLong(1);
                vkCreateFramebuffer(device, fbInfo, null, pFb);
                framebuffers.add(pFb.get(0));
            }
        }
    }

    private void createCommandPool() {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(graphicsFamily);

            LongBuffer pPool = stack.mallocLong(1);
            vkCreateCommandPool(device, poolInfo, null, pPool);
            commandPool = pPool.get(0);
        }
    }

    private void createVertexBuffer() {
        long bufferSize = (long) MAX_VERTICES * VERTEX_SIZE;

        try (MemoryStack stack = stackPush()) {
            VkBufferCreateInfo bufInfo = VkBufferCreateInfo.calloc(stack)
                .sType$Default()
                .size(bufferSize)
                .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            vkCreateBuffer(device, bufInfo, null, pBuffer);
            vertexBuffer = pBuffer.get(0);

            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, vertexBuffer, memReqs);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .allocationSize(memReqs.size())
                .memoryTypeIndex(findMemoryType(memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));

            LongBuffer pMemory = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMemory);
            vertexBufferMemory = pMemory.get(0);

            vkBindBufferMemory(device, vertexBuffer, vertexBufferMemory, 0);
        }
    }

    /**
     * Upload vertex data for this frame.
     * @param vertices float array: [x,y,z, r,g,b, x,y,z, r,g,b, ...]
     */
    public void uploadVertices(float[] vertices) {
        vertexCount = vertices.length / 6;
        if (vertexCount > MAX_VERTICES) {
            vertexCount = MAX_VERTICES;
        }

        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, vertexBufferMemory, 0, (long) vertexCount * VERTEX_SIZE, 0, pData);
            FloatBuffer fb = pData.getFloatBuffer(0, vertexCount * 6);
            fb.put(vertices, 0, vertexCount * 6);
            vkUnmapMemory(device, vertexBufferMemory);
        }
    }

    private void createCommandBuffers() {
        commandBuffers = new ArrayList<>();
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType$Default()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(MAX_FRAMES_IN_FLIGHT);

            PointerBuffer pBuffers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
            vkAllocateCommandBuffers(device, allocInfo, pBuffers);
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                commandBuffers.add(new VkCommandBuffer(pBuffers.get(i), device));
            }
        }
    }

    private void createSyncObjects() {
        imageAvailableSemaphores = new ArrayList<>();
        renderFinishedSemaphores = new ArrayList<>();
        inFlightFences = new ArrayList<>();

        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pSem = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                vkCreateSemaphore(device, semInfo, null, pSem);
                imageAvailableSemaphores.add(pSem.get(0));
                vkCreateSemaphore(device, semInfo, null, pSem);
                renderFinishedSemaphores.add(pSem.get(0));
                vkCreateFence(device, fenceInfo, null, pFence);
                inFlightFences.add(pFence.get(0));
            }
        }
    }

    /**
     * Render one frame with the given MVP matrix.
     */
    public void renderFrame(Matrix4f mvp) {
        try (MemoryStack stack = stackPush()) {
            vkWaitForFences(device, inFlightFences.get(currentFrame), true, Long.MAX_VALUE);

            IntBuffer pImageIndex = stack.mallocInt(1);
            int result = vkAcquireNextImageKHR(device, swapchain, Long.MAX_VALUE,
                imageAvailableSemaphores.get(currentFrame), VK_NULL_HANDLE, pImageIndex);

            if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapchain();
                return;
            }

            vkResetFences(device, inFlightFences.get(currentFrame));
            int imageIndex = pImageIndex.get(0);

            VkCommandBuffer cmd = commandBuffers.get(currentFrame);
            vkResetCommandBuffer(cmd, 0);
            recordCommandBuffer(cmd, imageIndex, mvp, stack);

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                .sType$Default()
                .pWaitSemaphores(stack.longs(imageAvailableSemaphores.get(currentFrame)))
                .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                .pCommandBuffers(stack.pointers(cmd))
                .pSignalSemaphores(stack.longs(renderFinishedSemaphores.get(currentFrame)));

            vkQueueSubmit(graphicsQueue, submitInfo, inFlightFences.get(currentFrame));

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                .sType$Default()
                .pWaitSemaphores(stack.longs(renderFinishedSemaphores.get(currentFrame)))
                .swapchainCount(1)
                .pSwapchains(stack.longs(swapchain))
                .pImageIndices(pImageIndex);

            result = vkQueuePresentKHR(presentQueue, presentInfo);
            if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                recreateSwapchain();
            }

            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
    }

    private void recordCommandBuffer(VkCommandBuffer cmd, int imageIndex, Matrix4f mvp, MemoryStack stack) {
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
        vkBeginCommandBuffer(cmd, beginInfo);

        VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
        clearValues.get(0).color().float32(0, 0.05f).float32(1, 0.05f).float32(2, 0.1f).float32(3, 1.0f); // dark blue bg
        clearValues.get(1).depthStencil().depth(1.0f).stencil(0);

        VkRenderPassBeginInfo rpInfo = VkRenderPassBeginInfo.calloc(stack)
            .sType$Default()
            .renderPass(renderPass)
            .framebuffer(framebuffers.get(imageIndex))
            .renderArea(a -> a.offset(o -> o.x(0).y(0)).extent(swapchainExtent))
            .pClearValues(clearValues);

        vkCmdBeginRenderPass(cmd, rpInfo, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);

        // Set dynamic viewport/scissor
        VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
            .x(0).y(0)
            .width(swapchainExtent.width())
            .height(swapchainExtent.height())
            .minDepth(0).maxDepth(1);
        vkCmdSetViewport(cmd, 0, viewport);

        VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack)
            .offset(o -> o.x(0).y(0))
            .extent(swapchainExtent);
        vkCmdSetScissor(cmd, 0, scissor);

        // Push MVP matrix
        ByteBuffer mvpBuf = stack.malloc(64);
        mvp.get(mvpBuf);
        vkCmdPushConstants(cmd, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, mvpBuf);

        // Bind vertex buffer and draw
        if (vertexCount > 0) {
            vkCmdBindVertexBuffers(cmd, 0, stack.longs(vertexBuffer), stack.longs(0));
            vkCmdDraw(cmd, vertexCount, 1, 0, 0);
        }

        vkCmdEndRenderPass(cmd);
        vkEndCommandBuffer(cmd);
    }

    public void recreateSwapchain() {
        vkDeviceWaitIdle(device);
        cleanupSwapchain();
        createSwapchain();
        createImageViews();
        createDepthResources();
        createFramebuffers();
    }

    private void cleanupSwapchain() {
        vkDestroyImageView(device, depthImageView, null);
        vkDestroyImage(device, depthImage, null);
        vkFreeMemory(device, depthImageMemory, null);
        for (long fb : framebuffers) vkDestroyFramebuffer(device, fb, null);
        for (long iv : swapchainImageViews) vkDestroyImageView(device, iv, null);
        vkDestroySwapchainKHR(device, swapchain, null);
    }

    public int getWidth() { return swapchainExtent.width(); }
    public int getHeight() { return swapchainExtent.height(); }

    public void cleanup() {
        vkDeviceWaitIdle(device);
        cleanupSwapchain();
        vkDestroyBuffer(device, vertexBuffer, null);
        vkFreeMemory(device, vertexBufferMemory, null);
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            vkDestroySemaphore(device, imageAvailableSemaphores.get(i), null);
            vkDestroySemaphore(device, renderFinishedSemaphores.get(i), null);
            vkDestroyFence(device, inFlightFences.get(i), null);
        }
        vkDestroyCommandPool(device, commandPool, null);
        vkDestroyPipeline(device, graphicsPipeline, null);
        vkDestroyPipelineLayout(device, pipelineLayout, null);
        vkDestroyRenderPass(device, renderPass, null);
        vkDestroyDevice(device, null);
        vkDestroySurfaceKHR(instance, surface, null);
        vkDestroyInstance(instance, null);
    }

    // ─── Inline shaders (compiled to SPIR-V at runtime via shaderc) ───

    private static final String VERTEX_SHADER_SRC = """
        #version 450
        
        layout(location = 0) in vec3 inPosition;
        layout(location = 1) in vec3 inColor;
        
        layout(push_constant) uniform PushConstants {
            mat4 mvp;
        } pc;
        
        layout(location = 0) out vec3 fragColor;
        
        void main() {
            gl_Position = pc.mvp * vec4(inPosition, 1.0);
            fragColor = inColor;
        }
        """;

    private static final String FRAGMENT_SHADER_SRC = """
        #version 450
        
        layout(location = 0) in vec3 fragColor;
        layout(location = 0) out vec4 outColor;
        
        void main() {
            outColor = vec4(fragColor, 1.0);
        }
        """;
}
