package xyz.erupt.core.controller;

import lombok.Cleanup;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.annotation.sub_field.Edit;
import xyz.erupt.annotation.sub_field.sub_edit.AttachmentType;
import xyz.erupt.core.annotation.EruptRouter;
import xyz.erupt.core.config.EruptConfig;
import xyz.erupt.core.constant.RestPath;
import xyz.erupt.core.exception.EruptNoLegalPowerException;
import xyz.erupt.core.service.CoreService;
import xyz.erupt.core.util.DateUtil;
import xyz.erupt.core.util.EruptSpringUtil;
import xyz.erupt.core.util.MimeUtil;
import xyz.erupt.core.view.EruptApiModel;
import xyz.erupt.core.view.EruptModel;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * @author liyuepeng
 * @date 10/15/18.
 */
@RestController
@RequestMapping(RestPath.ERUPT_FILE)
public class EruptFileController {

    @Autowired
    private EruptConfig eruptConfig;

    public static final String FS_SEP = "/";

    @PostMapping("/upload/{erupt}/{field}")
    @ResponseBody
    @EruptRouter(authIndex = 2)
    public EruptApiModel upload(@PathVariable("erupt") String eruptName, @PathVariable("field") String fieldName, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return EruptApiModel.errorApi("上传失败，请选择文件");
        }
        try {
            //生成存储路径
            String path = File.separator + DateUtil.getFormatDate(new Date(), DateUtil.DATE) + File.separator +
                    file.getOriginalFilename().replace("&", "") + "-" +
                    RandomUtils.nextInt(100, 9999);
            EruptModel eruptModel = CoreService.getErupt(eruptName);
            if (!eruptModel.getErupt().power().edit() && !eruptModel.getErupt().power().add()) {
                throw new EruptNoLegalPowerException();
            }
            Edit edit = eruptModel.getEruptFieldMap().get(fieldName).getEruptField().edit();
            switch (edit.type()) {
                case ATTACHMENT:
                    AttachmentType attachmentType = edit.attachmentType();
                    //校验扩展名
                    if (attachmentType.fileTypes().length > 0) {
                        String[] fileNameArr = file.getOriginalFilename().split("\\.");
                        String extensionName = fileNameArr[fileNameArr.length - 1];
                        if (!Arrays.asList(attachmentType.fileTypes()).contains(extensionName)) {
                            return EruptApiModel.errorApi("上传失败，文件格式不允许为：" + extensionName);
                        }
                    }

                    if (!"".equals(attachmentType.path())) {
                        if (attachmentType.path().startsWith(File.separator)) {
                            path = attachmentType.path() + path;
                        } else {
                            path = File.separator + attachmentType.path() + path;
                        }
                    }
                    //校验文件大小
                    if (attachmentType.size() > 0 && file.getSize() / 1024 > attachmentType.size()) {
                        return EruptApiModel.errorApi("上传失败，文件大小不能超过" + attachmentType.size() + "KB");
                    }
                    switch (edit.attachmentType().type()) {
                        case IMAGE:
                            AttachmentType.ImageType imageType = edit.attachmentType().imageType();
                            // 通过MultipartFile得到InputStream，从而得到BufferedImage
                            BufferedImage bufferedImage = ImageIO.read(file.getInputStream());
                            if (bufferedImage == null) {
                                return EruptApiModel.errorApi("获取图片流失败，请确认上传文件为图片");
                            }
                            if (imageType.width().length > 1 || imageType.height().length > 1) {
                                int width = bufferedImage.getWidth();
                                int height = bufferedImage.getHeight();
                                if (imageType.width().length > 1) {
                                    if (imageType.width()[0] > width || imageType.width()[1] < width) {
                                        return EruptApiModel.errorApi("上传失败，图片宽度不在["
                                                + imageType.width()[0] + "," + imageType.width()[1] + "]范围内");
                                    }
                                }
                                if (imageType.height().length > 1) {
                                    if (imageType.height()[0] > height || imageType.height()[1] < height) {
                                        return EruptApiModel.errorApi("上传失败，图片高度不在["
                                                + imageType.height()[0] + "," + imageType.height()[1] + "]范围内");
                                    }
                                }
                            }
                            break;
                        case OTHER:

                            break;
                    }
                    break;
                case HTML_EDIT:
                    break;
                default:
                    return EruptApiModel.errorApi("上传失败，非法类型!");
            }
            File dest = new File(eruptConfig.getUploadPath() + File.separator + path);
            if (!dest.getParentFile().exists()) {
                if (!dest.getParentFile().mkdirs()) {
                    return EruptApiModel.errorApi("上传失败，文件目录无法创建");
                }
            }
            //执行upload proxy
            for (Class<? extends DataProxy> clazz : eruptModel.getErupt().dataProxy()) {
                EruptSpringUtil.getBean(clazz).beforeUpLoadFile(file.getInputStream(), dest);
            }
            file.transferTo(dest);
            for (Class<? extends DataProxy> clazz : eruptModel.getErupt().dataProxy()) {
                EruptSpringUtil.getBean(clazz).afterUpLoadFile(dest, path);
            }
            return EruptApiModel.successApi(path.replace("\\", "/"));
        } catch (Exception e) {
            e.printStackTrace();
            return EruptApiModel.errorApi("上传失败，" + e.getMessage());
        }
    }

    @PostMapping("/upload-html-editor/{erupt}/{field}")
    @ResponseBody
    @EruptRouter(authIndex = 2, verifyMethod = EruptRouter.VerifyMethod.PARAM)
    public Map<String, Object> uploadHtmlEditorImage(@PathVariable("erupt") String eruptName,
                                                     @PathVariable("field") String fieldName,
                                                     @RequestParam("upload") MultipartFile file) {
        EruptApiModel eruptApiModel = upload(eruptName, fieldName, file);
        Map<String, Object> map = new HashMap<>(2);
        // ["uploaded":"true", "url":"image-path..."]
        if (eruptApiModel.getStatus() == EruptApiModel.Status.SUCCESS) {
            map.put("uploaded", true);
            map.put("url", RestPath.ERUPT_FILE + "/preview-attachment?path=" + eruptApiModel.getData());
        } else {
            map.put("uploaded", false);
        }
        return map;
    }


    @PostMapping("/uploads/{erupt}/{field}")
    @ResponseBody
    @EruptRouter(authIndex = 2)
    public EruptApiModel uploads(@PathVariable("erupt") String eruptName, @PathVariable("field") String fieldName, @RequestParam("file") MultipartFile[] files) {
        List<String> paths = new ArrayList<>();
        for (MultipartFile file : files) {
            EruptApiModel eruptApiModel = upload(eruptName, fieldName, file);
            paths.add(eruptApiModel.getMessage());
        }
        return EruptApiModel.successApi(String.join(",", paths));
    }

    @RequestMapping(value = "/download-attachment", produces = {MediaType.APPLICATION_OCTET_STREAM_VALUE})
    @ResponseBody
    public byte[] downloadAttachment(@RequestParam("path") String path, HttpServletResponse response) throws UnsupportedEncodingException {
        String[] split = path.split(FS_SEP);
        response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode(split[split.length - 1], "UTF-8"));
        return mappingFileToByte(path, response);
    }


    @RequestMapping(value = "/preview-attachment")
    @ResponseBody
    public void previewAttachment(@RequestParam("path") String path, HttpServletResponse response) throws UnsupportedEncodingException {
        String[] splitPath = path.split(FS_SEP);
        response.setHeader("Content-Disposition", "filename=" + java.net.URLEncoder.encode(splitPath[splitPath.length - 1], "UTF-8"));
        response.setContentType(MimeUtil.getMimeType(path));
        try (OutputStream ros = response.getOutputStream()) {
            IOUtils.write(mappingFileToByte(path, response), ros);
            ros.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] mappingFileToByte(String path, HttpServletResponse response) {
//        if (path.startsWith("http://") || path.startsWith("https://")) {
//
//        }
        if (!path.startsWith(FS_SEP)) {
            path = FS_SEP + path;
        }
        File file = new File(eruptConfig.getUploadPath() + path);
        try {
            @Cleanup InputStream inputStream = null;
            if (file.exists()) {
                inputStream = new FileInputStream(file);
            } else {
                inputStream = MimeUtil.class.getClassLoader().getResourceAsStream("empty.png");
                response.setContentType("image/png");
                response.setHeader("Content-Disposition", "filename=empty.png");
            }
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes, 0, inputStream.available());
            inputStream.close();
            return bytes;
        } catch (IOException e) {
            return null;
        }
    }

}
